# aria2 下载任务停机续传 + 做种保留 7 天：设计契约与实现（定稿）

> 状态：**已实现（P1–P4 代码已提交；P5 验收与 P6 文档进行中）**。本文件原为 P0 冻结契约，
> 现按实际落地代码校正：**类名 / 方法名 / 配置 key / 默认值 / 接口路径均以代码为准**，
> 与契约不一致之处集中记录在文末「9. 实现差异说明」，验收结论见文末「10. 验收结论」。
>
> 关键约束（来自需求方）：
> 1. **未修改 `async_task_record` 的表结构与实体字段**；检查点使用与它通过 `task_id` 关联的**独立新表** `async_task_checkpoint`。
> 2. aria2 视为独立守护进程（服务下线不影响它），**不保证** aria2 自身重启后的恢复。
> 3. 任意下载服务实例都可接管任务；因此多实例部署的前提是**共享数据源（MySQL）+ 共享下载目录 + 同一注册中心 + 同一 `sfc.internal.key`**。
> 4. 做种时长只在 core 统一配置中配置（全局项）。
> 5. 做种到期：停止做种 + 删除本地文件（网盘副本保留）。
> 6. 不自动提交 git。

---

## 0. 范围与非目标

**范围内**

- 异步任务框架新增「检查点持久化」+「可挂起任务（detach）」两项最小能力（`sfc-api` / `sfc-core-task` / `sfc-task-worker`）。
- `sfc-download-service` 的 P2P（MAGNET/TORRENT）任务：检查点落盘、停机挂起、重启/跨实例接管、aria2 孤儿任务对账、上传阶段幂等。
- 做种保留：统一配置、做种生命周期表、到期清理调度、同 infohash 复用、列表展示字段。
- `sfc-download-service` 数据源可切换（SQLite 默认 / MySQL），供多实例共享状态。

**非目标**

- aria2 进程重启后的任务恢复（仅做「同目录 `continue` 尽力续传」，不作承诺）。
- HTTP / Git / ED2K 下载行为变更。
- 前端构建产物 `static/index.umd.js` 的 UI 改动（只提供接口字段并写前端适配文档）。
- SQLite → MySQL 数据迁移。
- 修改 `async_task_record` 表结构或实体字段。

---

## 1. 总体数据流

```
创建任务（下载服务 API）
  └─ 写 download_task → 提交 async_task_record（core DB，共享）

Worker 认领（core 下发 TaskDispatchInfo：params + checkpoint）
  └─ DownloadTaskHandler
       ├─ 校验保存目录
       ├─ P2P：P2pTaskCoordinator.start()/resume()（落 P2pTaskState 检查点）
       │     └─ aria2 JSON-RPC（外部进程，服务下线后继续下载）
       ├─ 上传 core 网盘（上传前后更新检查点 phase）
       ├─ 下载完成时登记 download_seed_task（做种保留）
       └─ 上报 FINISH / FAILED / CANCEL

停机/失联：
  优雅停机 → SDK 对支持 detach 的任务 requestDetach() → handler 保存检查点后退出
           → 上报 abandon(detached=true) → 任务回到等待中（不计重试）
  强制崩溃 → 健康检查 / 启动恢复回收 → 任务回到等待中（有检查点的任务不计重试）

重启/其它实例：
  认领 → core 下发检查点 → P2pTaskCoordinator.resume() → tellStatus 重连原 gid
  → 继续进度上报 → 采集产物 → 上传 → FINISH

做种期（异步任务已 FINISH）：
  SeedTaskKeeper 周期扫描 download_seed_task
  → 到期：forceRemove/removeDownloadResult + 删除本地目录 → 置 CLEANED
```

---

## 2. 契约 A：异步任务框架最小扩展

### 2.1 检查点表（`sfc-api`）

新增实体 `com.sfc.task.model.AsyncTaskCheckpoint`（**不继承** `SfcAuditModel`，主键就是 taskId）：

```java
@Entity
@Table(name = "async_task_checkpoint")
public class AsyncTaskCheckpoint {
    @Id
    @Column(name = "task_id")
    private Long taskId;        // 与 async_task_record.id 一一对应，非自增
    @Column(name = "task_type", length = 64)
    private String taskType;
    @Lob
    private String checkpoint;  // JSON 文本，语义由任务实现自定义
    @Column(name = "update_at")
    private Date updateAt;
}
```

新增仓库 `com.sfc.task.repo.AsyncTaskCheckpointRepo extends JpaRepository<AsyncTaskCheckpoint, Long>`：
`findByTaskId`、`deleteByTaskId`（`@Modifying`）、`deleteByUpdateAtBefore`（`@Modifying`，过期清理）、
`findExistingTaskIds(Collection<Long>)`（故障转移时批量判定哪些任务带检查点）。

> `async_task_record` 实体、表、DDL 一律不动。`ddl-auto=update` 只负责创建新表。

### 2.2 core 控制面（`sfc-core-task`）

新增 `com.sfc.task.report.TaskCheckpointService`：

| 方法 | 语义 |
|---|---|
| `void save(Long taskId, String taskType, String checkpoint)` | upsert（先查后存）；空 checkpoint 视为删除；超过 `MAX_CHECKPOINT_LENGTH`（1MB，1024 * 1024 字符）拒绝保存并 WARN |
| `String find(Long taskId)` | 无则返回 `null` |
| `void delete(Long taskId)` | 任务进入终态时清理 |
| `int cleanupExpired(Duration maxAge)` | 清理超过 30 天未被更新的残留检查点（`@Scheduled` 每日一次，启动 10 分钟后首跑） |
| `Set<Long> findExistingTaskIds(Collection<Long> taskIds)` | 故障转移回收时批量判定哪些任务带检查点（500/批；查询失败由调用方回退普通回收） |

内部协议端点（`TaskWorkerInternalController`）：

| 端点 | 请求 | 响应 | 校验 |
|---|---|---|---|
| `POST /api/internal/task/checkpoint/save` | `TaskCheckpointSaveParam` | `SfcJsonResult<Boolean>` | taskId 存在、status=1(RUNNING)、`claimedWorker == workerId`；否则返回 false（不报错）；`taskId` / `workerId` 为空同样返回 false；空白 checkpoint 视为删除 |

认领下发：`TaskWorkerInternalController#toDispatchInfo(record)` 读取检查点填入 `TaskDispatchInfo.checkpoint`（无则 `null`）。

终态清理（`TaskReportService`）：
- `reportFinish`（FINISH / FAILED / CANCEL）成功后删除检查点；
- `cancelRunningTask`、`failTempTask`、`markFailedIfRetryExceeded` 置终态时删除；
- `reportAbandon`（普通 / detached）与 `reclaimDeadWorkerTasks`（重新排队）**保留**检查点；
- 删除失败只 WARN，残留由每日过期清理兜底；`reclaimDeadWorkerTasks` 用 `findExistingTaskIds` 批量判定（500/批），
  查询失败时回退为普通故障转移（消耗重试次数），避免无限重排。

**重试计数修正（重要）**：执行者主动停机挂起（`detached=true`）与「带检查点」任务的失联回收**不得消耗** `retry_count`，
否则服务重启 3 次就会把长下载任务判失败：
- `TaskAbandonParam` 新增 `Boolean detached`；`reportAbandon(detached=true)` 走新增仓库方法
  `requeueDetached(taskId)`（`status=1` → `status=0`，清空 claimed*，**不动 retry_count**）。
- `reclaimDeadWorkerTasks` 对**存在检查点的记录**同样走 `requeueDetached`；无检查点的沿用 `abandonAndRequeue`（+1，超限失败）。
- 防死循环：接管预算放在**检查点内部**（下载服务自己维护 `resumeCount`，超限时主动上报 FAILED），不新增 `async_task_record` 字段。

`AsyncTaskConstants`：**不新增状态值**。detach/挂起语义仍复用「等待中（0）」。

### 2.3 SDK（`microservices/sfc-task-worker`）

**协议（`protocol` 包，唯一来源）**

- `TaskDispatchInfo` 末尾新增字段 `String checkpoint`（位置无关，Jackson 按名反序列化）。
- 新增 `TaskCheckpointSaveParam(Long taskId, String workerId, String taskType, String checkpoint)`。
- `TaskAbandonParam` 末尾新增字段 `Boolean detached`。

**客户端**：`SfcTaskWorkerClient` 新增 `boolean saveCheckpoint(TaskCheckpointSaveParam param)`（沿用现有出站凭证与负载均衡拦截器）。

**`SfcTaskHandler`**

```java
/** 是否支持「挂起」：true 时 Worker 停机不中断任务，而是请求挂起并保留外部工作 */
default boolean supportsDetach() { return false; }
```

**`SfcTaskExecution`**

| 成员 | 语义 |
|---|---|
| `String getCheckpoint()` | 下发时携带的检查点（`saveCheckpoint` 后返回最近一次写入值，即使该次同步上报失败） |
| `boolean saveCheckpoint(String checkpoint)` | 同步（带有限重试）上报 core；失败只 WARN 并记为待重发（由 `tick()` 补发），**不向 handler 抛异常**；返回本次是否已同步成功；任务已结束或空白值不保存 |
| `boolean isDetachRequested()` | 是否收到挂起指令 |
| `void onDetach(Runnable)` | 注册挂起回调；已挂起时立即执行；与 `onInterrupt` 相互独立 |
| package `requestDetach()` | 仅 SDK 调用：置位并触发回调（不触发中断回调） |
| package 检查点待重发方法 | `getPendingCheckpoint()` / `markPendingCheckpoint(String)` / `clearPendingCheckpoint(String)`，仅供 `SfcTaskReporter` 补发使用 |

**新异常**：`com.sfc.cloud.task.worker.SfcTaskDetachedException extends RuntimeException`
—— 任务实现检测到挂起后抛出它（或正常返回），SDK 都按「已挂起」处理，**不报 FINISH**。

**`SfcTaskReporter`**

- `reportAbandon(SfcTaskExecution execution, String message)`：内部构造 `TaskAbandonParam(..., message, detached)`，`detached = execution.isDetachRequested()`。
- `saveCheckpoint(SfcTaskExecution execution, String checkpoint)`：带重试的关键上报；失败不抛。
- `tick()`：若执行上下文有待重发检查点，先补发。

**`SfcTaskExecutionRunner`**

- `catch (SfcTaskDetachedException)` → 不写终态，`finally` 中若 `isDetachRequested()` 则
  `reporter.reportAbandon(execution, "执行者停机，任务已挂起，等待接管")`，否则才 `reportFinish`。
- 正常返回但 `isDetachRequested()` 为 true → 同样按挂起（abandon）处理。
- 注意：一旦收到挂起指令，即使 handler 抛出的是普通异常，`finally` 仍按挂起语义上报 abandon（挂起优先于失败），
  以保证检查点与外部现场不被失败清理逻辑破坏。

**`SfcTaskWorkerRuntime.stop()`（改造顺序）**

1. 停止认领线程、停止 tick 调度（现状不变）。
2. 遍历运行中执行上下文：
   - `handler.supportsDetach()` 为 true → `execution.requestDetach()`（**不** requestInterrupt、**不** unregister）；
   - 否则 → 维持现状：`requestInterrupt()` + `unregisterExecution` + `reportAbandon`（普通语义，计重试）。
3. `taskExecutor.shutdown()` + `awaitTermination(detachAwaitSeconds)`；detach 任务应在此窗口内保存检查点并退出（runner 负责上报 abandon(detached)）。
4. await 结束后做最后清扫：仍登记的执行上下文 → `requestInterrupt()` + `unregisterExecution` + `reportAbandon`；
   已收到挂起指令的上下文仍按挂起语义上报（保留检查点、**不**消耗重试次数），用于兜底而非惩罚。
5. 停机窗口内新登记的执行上下文：沿用现有补报逻辑。

> 与初版契约差异：初版写「兜底清扫计入重试」，实际实现按实际语义（detached）上报，见文末差异清单 B。

新增配置 `sfc.task.worker.detach-await-seconds`，默认 **10**（`SfcTaskWorkerProperties`）。

**兼容性**：所有改动为新增方法/新增字段/新异常；既有 `SfcTaskHandler` 实现（含 core 内嵌 Worker 的 `AsyncTaskFactoryHandler`）零迁移。core 与 worker SDK 必须同版本升级（同仓库同次发布）。

---

## 3. 契约 B：下载服务 P2P 接管

### 3.1 检查点 JSON（`P2pTaskState`，存 core 的 `async_task_checkpoint`）

```json
{
  "version": 1,
  "downloadId": 1234567890,
  "phase": "DOWNLOADING",
  "localDir": "/shared/download/offline-download/1234567890-<uuid>",
  "aria2Dir": "/downloads/offline-download/1234567890-<uuid>",
  "gids": ["abc123"],
  "contentGids": ["abc123"],
  "infohash": "08ada5a7...",
  "resumeCount": 0,
  "submittedAt": 1750000000000,
  "updatedAt": 1750000000000,
  "seeding": true,
  "seedTaskId": 998877,
  "seedEndAt": 1750604800000,
  "savedName": null,
  "savedBytes": 0,
  "uploadStartedAt": null,
  "uploaded": false
}
```

`phase` 状态机：

```
PREPARED ──提交 aria2──► SUBMITTED ──首轮状态──► DOWNLOADING
   │                                              │
   └────────── 失败/取消清理 ◄────────────────────┘
                                                  │ 全部 gid complete + 产物采集
                                                  ▼
                                              DOWNLOADED ──(登记做种)──► [seeding=true]
                                                  │
                                                  ▼
                                              UPLOADING ──上传成功──► UPLOADED ──► FINISH
```

规则：
- `gids` 为已跟踪的全部 aria2 gid（磁力链含元数据 gid 与内容 gid）；`contentGids` 为真正产出文件的 gid。
- `localDir` / `aria2Dir` 在**首次创建后不再变化**（恢复时复用同一目录，不再新建）。
- 每次 gid 集合变化、阶段变化、detach、上传前后必须 `saveCheckpoint`；轮询期间引擎通过 `sessionListener`
  在 gid 跟随与约 60 秒（`CHECKPOINT_REFRESH_INTERVAL_MILLIS = 60_000`）心跳时回调协调器刷新检查点 `updatedAt`。
- `resumeCount`：每次「接管」+1；超过 **50** 次视为异常，主动上报 FAILED 并清理（防死循环）。
- `P2pTaskState` 反序列化失败、版本不认识（`CURRENT_VERSION = 1`）、缺少 `phase`、或 `downloadId` 与当前任务不一致：
  记 WARN，按「无检查点」处理（重新下载），不抛异常卡死任务。
- 实际 `phase` 为枚举 `P2pTaskState.Phase`（JSON 中序列化为枚举名），并提供 `isDownloading()`（`SUBMITTED` / `DOWNLOADING`）。

### 3.2 组件与职责（`engine/p2p`）

| 组件 | 职责（以下为实际类与方法） |
|---|---|
| `P2pTaskCoordinator` | P2P 任务总编排：`loadResumable(context)` 读取检查点、`start(context)` 新建、`resume(state, context)` 接管、`collectExisting(state, context)` 采集既有产物、上传幂等（`awaitUploadSettled` / `markUploading` / `markUploaded`）与收口（`finishSuccess` / `releaseOnFailure` / `releaseOnCancel`）；方法返回 `P2pTaskResult(P2pTaskState state, List<DownloadArtifact> artifacts)`；不负责上传 |
| `P2pTaskState` | 检查点模型 + JSON（`toJson()` / `fromJson()`）+ `Phase` 枚举（6 个阶段，`isDownloading()`） |
| `Aria2TaskSession` | 独立顶层类：跟踪全部 gid / contentGid、infohash、`pollTargets()`；磁力链元数据→内容 gid 跟随在引擎轮询中完成 |
| `Aria2DownloadEngine` | 提供 aria2 原子能力：`submitSession` / `resumeSession` / `filterAliveGids` / `awaitCompletion`（含 60 秒检查点心跳回调）/ `collectArtifacts` / `discardTaskResults`；不再自行清理任务结果 |
| `Aria2OrphanReconciler` | aria2 孤儿任务对账（见 3.6） |
| `P2pSeedRegistrar`（SPI 接口） | `register(DownloadContext, P2pTaskState, List<DownloadArtifact>)` / `findReusable(DownloadTaskParams)` / `isProtectedDirectory(Path)`；实现由做种阶段（P3）提供，取不到时用 `NoopP2pSeedRegistrar` 兜底 |
| `P2pSeedReuseTarget` | 复用目标 record：`seedTaskId / downloadId / contentGid / aria2Dir / localDir / seedEndAt` |
| `Aria2Workspace`（增强） | `workspaceRoot()` 工作区根、`isInWorkspace(Path)` 路径归属、`parseDownloadId(Path)` 从目录名解析下载 id、`toLocalDir(String)` 目录映射、`installProtectedDirectoryProbe()` 把做种保护桥接给陈旧清理 |

### 3.3 执行流程（Handler 视角）

```
execute(execution):
  params = parseParams; context = new DownloadContext(...)
  try:
    updateStatus(RUNNING); execution.onInterrupt(context::cancel)
    type = params.type; P2P = type in {MAGNET, TORRENT}
    if P2P:
        state = coordinator.loadResumable(context)            # 读取并校验检查点
        if state != null && state.isUploaded():
            finalizeP2pFromCheckpoint(state); return          # 已上传成功：直接补报 FINISH
        else if state != null && state.isPhase(UPLOADING):
            latest = coordinator.awaitUploadSettled(context, state)  # 上传幂等窗口（默认 10 分钟/3 次×10 秒）
            if latest.isUploaded(): finalizeP2pFromCheckpoint(latest); return
            artifacts = coordinator.collectExisting(latest, context)
        else if state != null:
            artifacts = coordinator.resume(state, context)     # 重连 gid / 同目录续传 / 采集既有产物
        else:
            artifacts = coordinator.start(context)             # 新建（含做种复用查询）
        context.checkDetach(); context.checkInterrupted()
        coordinator.markUploading(context, state)              # UPLOADING + uploadStartedAt
        result = stash(artifacts)                              # 上传（不可中断语义）
        coordinator.markUploaded(context, state, ...)          # UPLOADED + savedName/savedBytes
        if context.isDetachRequested(): throw new SfcTaskDetachedException(...)  # 上传完成后挂起
        finalizeP2pSuccess(...)                                # FINISH + 收口矩阵
    else:
        validateSavePath(context)
        artifacts = engineRegistry.get(type).execute(context)
        context.checkDetach()                                  # 无外部现场：按取消语义收尾后挂起
        stash(...)                                             # 现状不变
```

- 保存目录校验保持现状（先校验再下载）；接管路径同样先校验。
- 取消语义：`context.checkInterrupted()` 照旧；取消 / 失败 / 成功分别由 `releaseOnCancel` / `releaseOnFailure` /
  `finishSuccess` 按 3.4 收口矩阵处理。
- 日志/进度/任务展示名：沿用现有逻辑；接管时在任务日志输出「已接管既有 aria2 任务: gid=...」。
- 挂起检测方式：**轮询** `DownloadContext.isDetachRequested()`（而非依赖 `onDetach` 回调保存检查点），
  检测到后由协调器保存检查点并抛 `SfcTaskDetachedException`（见文末差异 A/1）。

### 3.4 生命周期收口矩阵（本地目录 + aria2 结果）

| 结局 | aria2 处理 | 本地目录 | 检查点 |
|---|---|---|---|
| 下载完成 + 做种已登记 | **保留**（做种中） | **保留**（记入 `download_seed_task`） | 保留至终态 → FINISH 后删除 |
| 下载完成 + 做种未启用（配置 0 / SPI 返回 false） | `removeDownloadResult` | 删除 | FINISH 后删除 |
| 下载完成后上传失败（做种已登记） | 保留（做种继续） | 保留（SeedTaskKeeper 到期清理） | FAILED 终态删除 |
| 下载完成后上传失败（无做种） | `removeDownloadResult` | 删除 | FAILED 终态删除 |
| 用户取消 | `forceRemove` + `removeDownloadResult` | 删除 | CANCEL 终态删除 |
| 下载阶段失败 | `forceRemove` + `removeDownloadResult` | 删除 | FAILED 终态删除 |
| **挂起（detach）** | **不动** | **不动** | 保留（下次接管用） |

实现要点：
- 已移除 `DownloadTaskHandler` 对 P2P 任务目录的无条件删除；只有收口矩阵判定为「删除」时才删。
- `Aria2DownloadEngine.execute` 原 `finally` 中的无条件 `forceRemove + removeDownloadResult` 已移除，
  改由 `P2pTaskCoordinator` 的收口方法（`finishSuccess` / `releaseOnFailure` / `releaseOnCancel`）执行。
- 上传阶段被 detach 时**允许上传继续完成**（上传不可中断语义），完成后写 `UPLOADED` 再退出；超过 `detachAwaitSeconds` 由 SDK 兜底中断。

### 3.5 上传阶段幂等

`DownloadTaskHandler` 在上传前后更新检查点：

1. 上传前：`phase=UPLOADING, uploadStartedAt=now` 并保存；
2. 上传成功：`phase=UPLOADED, uploaded=true, savedName, savedBytes` 并保存；
3. 若此时 `execution.isDetachRequested()` → 抛出 `SfcTaskDetachedException`（任务回队列，由接管实例直接补报 FINISH，不再重复上传）。

接管实例遇到 `phase=UPLOADING` 时（`P2pTaskCoordinator#awaitUploadSettled`）：
- 若 `now - uploadStartedAt < sfc.download.resume.upload-stale-millis`（默认 `600000` 毫秒 = 10 分钟）→
  认为旧实例可能仍在上传：最多等待 `sfc.download.resume.upload-wait-attempts`（默认 3）次、每次
  `sfc.download.resume.upload-wait-interval-millis`（默认 10000 毫秒），期间重读**本地执行上下文的检查点**；
  仍未变成 `UPLOADED` 则按陈旧处理；
- 陈旧 → 重新上传（记 WARN，**可能出现重复文件**，由 core 落盘侧的重名策略兜底）；
- `phase=UPLOADED` → 直接补报 FINISH（带 `savedName`、`savedBytes`），不重复上传；
- 等待期间收到挂起请求 → 保存检查点并抛 `SfcTaskDetachedException`。

> 已知限制（见文末差异 3）：Worker SDK 未暴露「从 core 重新读取检查点」的接口，`awaitUploadSettled` 实际只能观察
> 本实例已知的最新检查点值，等待窗口主要用于给「旧实例完成上传」留出时间；超时后按陈旧重传。

### 3.6 aria2 孤儿任务对账（`Aria2OrphanReconciler`）

触发：应用启动完成后一次 + 周期（`sfc.download.aria2.reconcile-interval-millis`，默认 `300000`，`<=0` 关闭）。
实现使用自带单线程调度器（`scheduleWithFixedDelay`）而非 `@Scheduled`，以便表达「0 = 关闭」。

安全前提（**必须遵守**）：只处理 `dir` 映射后位于本服务任务工作区（`<sharedDir>/offline-download/` 或 JVM 临时目录
`offline-download/`）下的 aria2 任务（`Aria2Workspace#isInWorkspace`）；**绝不触碰**工作区之外的 aria2 任务。

对每个候选 gid：
1. 从 `dir` 目录名解析 `<downloadId>-`（`Aria2Workspace#parseDownloadId`）；
2. 查 `download_task`：
   - `status ∈ {RUNNING(1), WAITING(0)}` → 跳过（可能等待接管）；
   - `status ∈ {FINISH(2), FAILED(3), CANCEL(4)}` 且**不是**做种保护目录 → 清理（`forceRemove` + `removeDownloadResult` + 删除目录）；
   - `status = OFFLINE(5)` 或未知状态 → 保守跳过（不清理）；
   - **记录不存在** → 只记 WARN，不删除（避免误删其它部署/实例的数据）；
3. 对处于做种保护中的目录 → 跳过（由 SeedTaskKeeper 负责）；做种保护查询失败 → 跳过该候选（fail-safe）。

候选来源：`Aria2Client` 的 `tellActive(List<String> keys)` + `tellWaiting(0, 1000, keys)` + `tellStopped(0, 1000, keys)`
（aria2 标准 RPC，返回 `List<Map>`；`tellWaiting` / `tellStopped` 单页取 1000 条）。aria2 不可达时只 WARN 并跳过本轮，
不置任何终态。

### 3.7 配置与选项

- `Aria2Property` 新增 `seedTimeMinutes`（见 4.1），由 `resolveSeedTimeMinutes()` 统一解析。
- `buildAria2Options`：先应用用户白名单选项，再覆盖服务端强制项：`dir`、`seed-time=<生效做种分钟数>`、
  `continue=true`（强制，保证 gid 丢失后同目录重提可续传）、`bt-save-metadata=false`；**刻意不设置 `follow-torrent`**。
- `Aria2Workspace.installProtectedDirectoryProbe()` / `DownloadFileUtils.setProtectedDirectoryProbe(Predicate<Path>)`：
  陈旧清理前排除「做种保护目录」；探测器查询失败则**跳过本轮清理**（fail-safe）。
- `SfcDownloadInternalProperties` 新增：`aria2.reconcile-interval-millis`（默认 300000）与
  `resume.upload-stale-millis`（默认 600000）、`resume.upload-wait-interval-millis`（默认 10000）、
  `resume.upload-wait-attempts`（默认 3），对应 key 前缀 `sfc.download.*`。

---

## 4. 契约 C：做种保留

### 4.1 统一配置（core 面板自动出现）

`Aria2Property` 新增：

```java
@ConfigProperty(
    title = "做种保留时长(分钟)",
    describe = "磁力/种子下载完成后保留本地文件做种分享的时长（分钟）；0=不做种，完成后立即清理本地文件",
    defaultValue = "10080")
private Integer seedTimeMinutes = 10080;

/** 做种保留时长的配置上限（分钟）：1 年 */
public static final int MAX_SEED_TIME_MINUTES = 525600;

public int resolveSeedTimeMinutes() {
    Integer configured = seedTimeMinutes;
    if (configured == null || configured <= 0) return 0;
    if (configured > MAX_SEED_TIME_MINUTES) {
        log.warn("做种保留时长配置超过上限，按上限生效: configured={}分钟, limit={}分钟", configured, MAX_SEED_TIME_MINUTES);
        return MAX_SEED_TIME_MINUTES;
    }
    return configured;
}
```

- key：`download.aria2.seed-time-minutes`；由 `config-properties.json` 中 `download-aria2` 分组
  （标题「aria2 P2P 下载」）的 `typeRef=com.sfc.download.model.property.Aria2Property` 自动展开。
- 取值规则：`null` / `<=0` → `0`；上限 `525600`（1 年），超出按上限并 WARN；引擎与做种登记都必须调用
  `resolveSeedTimeMinutes()`，保证两处取值一致。
- 生效时机：任务提交给 aria2 时读取快照；修改后仅对**新任务**生效（进行中任务沿用提交时的值）。

### 4.2 表 `download_seed_task`（下载服务库）

继承 `SfcAuditModel`（雪花主键 + `uid` + 审计字段），`@Table(name = "download_seed_task", indexes = {download_id 唯一, infohash 普通, status+seed_end_at})`。
实际索引名为 `idx_download_seed_task_download_id`（unique）/ `idx_download_seed_task_infohash` /
`idx_download_seed_task_status_seed_end_at`；多词列名显式声明（`aria2_dir` / `torrent_sha256` / `local_dir` /
`seed_start_at` / `seed_end_at` / `finish_at`），保证 Hibernate 生成的列名与契约逐字一致：

| 字段 | 类型 | 说明 |
|---|---|---|
| `download_id` | Long，唯一 | 关联 `download_task.id`（一个下载任务最多一条做种记录） |
| `content_gid` | String(64) | 真正产出内容的 aria2 gid（用于到期清理） |
| `infohash` | String(64) | 小写 hex；磁力链可解析，种子任务取自 aria2 `bittorrent.infoHash`；未知为 null |
| `torrent_sha256` | String(64) | 种子任务可选：种子内容的 SHA-256（用于同 infohash 复用匹配），磁力为 null |
| `aria2_dir` | String(2048) | aria2 侧目录 |
| `local_dir` | String(2048) | 本服务侧目录（到期删除目标） |
| `seed_start_at` | Date | 下载完成时间 |
| `seed_end_at` | Date | `seed_start_at + seedTimeMinutes` |
| `status` | Integer | 0=做种中，1=清理中（抢占），2=已清理，3=异常 |
| `message` | String(1024) | 清理结果/异常信息 |
| `finish_at` | Date | 进入终态时间 |

### 4.3 `SeedTaskKeeper`（`com.sfc.download.seed`）

- `@EnableScheduling` 加在下载服务配置类（新增 `SfcDownloadSchedulingConfig`）。
- 定时：`@Scheduled(fixedDelayString = "${sfc.download.seed.check-interval-millis:60000}")`。
- 启动补扫：`ApplicationReadyEvent` 触发一次（放在虚拟线程执行，避免拖慢应用就绪）。
- 单轮算法（`SeedTaskKeeper#scan(Date)`）：
  1. 先用 `Aria2Client#getVersion()` 探测 aria2 可达性，不可达时只 WARN 并**跳过整轮**（不置终态）；
  2. 回收「清理中」超时记录：`status=1 AND update_at < now - 10 分钟`，重新执行幂等清理；
  3. `findExpired(status=0 AND seed_end_at <= now, limit=200)`；
  4. 逐条抢占：`UPDATE ... SET status=1 WHERE id=? AND status=0`（多实例幂等，抢不到跳过）；
  5. 清理：`tellStatus(gid, ["status"])` → 若仍 `active`/`waiting`/`paused` 则 `forceRemove(gid)` →
     `removeDownloadResult(gid)` → `DownloadFileUtils.deleteQuietly(localDir)`；
  6. 成功置 `status=2, finish_at=now, message=...`（批量 `updateFinishByIds`）；本地目录删除后仍存在等异常置
     `status=3` 并记录原因（逐条 `updateFinish`）。
- 批量用模块内 `SeedTaskBatchUtils.partition` 分批（下载服务零宿主依赖，无法引用宿主 `CollectionUtils`；行为等价）。
- aria2 单条移除失败与本地目录删除**互不阻塞**：先移除 aria2，再删目录；aria2 失败也继续删目录并把原因拼进 `message`。

### 4.4 下载完成登记（`P2pSeedRegistrarImpl`）

- `register(context, state, artifacts)`（`P2pSeedRegistrarImpl`）：
  - `configService.getAria2().resolveSeedTimeMinutes() <= 0` → 返回 false（不保留，走「完成即清理」）；
  - 计算 `seedEndAt = seedStartAt + minutes`；插入 `download_seed_task`（`download_id` 唯一；重复插入捕获唯一冲突后回查已存在记录）；
  - 已存在记录按「已存在」处理（复用原记录与到期时间，**不延长**）；若已有记录状态不是「做种中」则返回 false（不保留）；
  - infohash：优先取检查点（磁力链解析 / 种子任务取自 aria2 `bittorrent.infoHash`），回退为从磁力链解析；
  - `torrent_sha256`：**仅当任务参数携带 `torrentBase64` 时**计算（仅 `.torrent` URL 的种子任务无法计算，见差异 9）；
  - 回写检查点 `seeding=true, seedTaskId, seedEndAt`（由协调器在调用返回后统一保存）；
  - 返回 true（目录保留）。
- `findReusable(params)`：按 infohash（磁力）/`torrent_sha256`（种子）查 `status=0 AND seed_end_at > now` 且
  `local_dir` 存在的记录 → 返回 `P2pSeedReuseTarget`；查询只读、无副作用。
- `isProtectedDirectory(Path dir)`：`status=0` 且规范化后 `local_dir` 等于该目录（先按精确匹配走索引，未命中再拉取
  生效中目录逐一比对）；查询失败**必须抛出**（由调用方 fail-safe 跳过本轮清理）。

### 4.5 同 infohash 复用（避免 `InfoHash is already registered`）

任务开始（P2P 提交前）调用 `findReusable`：

- **命中**：不再提交 aria2，直接把检查点置为 `DOWNLOADED`（`localDir` / `gids` 取自做种记录），走「读取现有文件 → 上传 → FINISH」；
  任务日志说明「复用做种中的文件，无需重新下载（做种保留至 ...）」。做种到期时间不变。
- **未命中**：正常提交；极端竞态/无法匹配时 aria2 可能返回 `InfoHash is already registered`，此时任务以 aria2
  **原始错误文本**失败（**未实现**初版契约中的可读 409 提示，见文末差异 A/4）；稍后重试或等做种到期清理后再试即可。
- 磁力 infohash 解析（`SeedHashUtils#parseMagnetInfoHash`）：`xt=urn:btih:<40hex>` 或 `<32 base32>`（转小写 hex）；
  解析失败不阻断任务，只是失去复用能力。
- 种子任务复用按 `torrentBase64` 的 SHA-256 匹配；仅提供 `.torrent` URL 时无法计算指纹，不具备复用能力。

### 4.6 列表展示字段

- `DownloadTaskInfo` 新增 `@Transient` 字段：`Boolean seeding`、`Date seedEndAt`（不落库）。
- 列表/详情接口（`DownloadServiceImpl#fillSeedingFields`，在 `getTaskList` 中调用）按 downloadId 批量查
  `download_seed_task`（`SeedTaskBatchUtils.partition` 分批，取生效中状态 `SEEDING` / `CLEANING`）；做种记录查不到 →
  `seeding=false`（显式写入，保证字段始终存在）；查询失败只 WARN 并回退为未做种，不影响列表接口。
- 前端适配文档更新：说明两个字段含义（不改前端构建产物）。

---

## 5. 契约 D：下载服务数据源可切换（SQLite / MySQL）

`microservices/sfc-download-service/pom.xml` 新增（版本由 Spring Boot BOM 管理）：
`com.mysql:mysql-connector-j`（runtime）；`org.springframework.boot:spring-boot-starter-test`（test，供单测，P2 已加入）。
`application.yml` 参数化（默认值保持 SQLite 不变）：

```yaml
spring:
  datasource:
    driver-class-name: ${SFC_DOWNLOAD_DB_DRIVER:org.sqlite.JDBC}
    url: ${SFC_DOWNLOAD_DB_URL:jdbc:sqlite:./download.db?journal_mode=WAL&busy_timeout=5000}
    username: ${SFC_DOWNLOAD_DB_USERNAME:}
    password: ${SFC_DOWNLOAD_DB_PASSWORD:}
    hikari:
      maximum-pool-size: ${SFC_DOWNLOAD_DB_POOL_SIZE:1}
      minimum-idle: ${SFC_DOWNLOAD_DB_MIN_IDLE:1}
```

MySQL 示例（写入 README）：

```
SFC_DOWNLOAD_DB_URL=jdbc:mysql://127.0.0.1:3306/sfc_download?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
SFC_DOWNLOAD_DB_DRIVER=com.mysql.cj.jdbc.Driver
SFC_DOWNLOAD_DB_USERNAME=sfc
SFC_DOWNLOAD_DB_PASSWORD=******
SFC_DOWNLOAD_DB_POOL_SIZE=8
SFC_DOWNLOAD_DB_MIN_IDLE=2
```

- SQLite 专属 URL 参数（`journal_mode`、`busy_timeout`）与单连接池只体现在默认值里；切 MySQL 时由 env 覆盖，无需改 yml。
- `ddl-auto=update` 负责建表；**不做数据迁移**（切库即新库），README 已写明。
- **多实例 / 跨实例接管前提（已写入 README）**：① 共享数据库用 MySQL（SQLite 仅同实例重启接管）；
  ② 各实例相同 `shared-dir` / `remote-dir` 且映射同一份共享存储；③ 相同 `sfc.internal.key`；④ 同一注册中心。
- 容器镜像默认 `ENV SFC_DOWNLOAD_DB_URL="jdbc:sqlite:/data/download.db?..."`；切 MySQL 需显式覆盖
  `SFC_DOWNLOAD_DB_DRIVER` / `SFC_DOWNLOAD_DB_URL` 等环境变量（Dockerfile 未在本次改动范围）。

---

## 6. 阶段、职责与文件所有权

| 阶段 | 任务 | 负责人 | 依赖 | 写入范围 |
|---|---|---|---|---|
| P0 | 本设计契约 | Lead | — | 本文件 |
| P1 | 框架能力 | `task-framework-dev` | P0 | `sfc-api/src/main/java/com/sfc/task/{model,repo}/…`、`sfc-core-task/src/main/java/com/sfc/task/**`、`sfc-core-task/src/test/**`、`microservices/sfc-task-worker/src/main/java/**`、`microservices/sfc-task-worker/src/test/**`、`microservices/sfc-task-worker/pom.xml` |
| P2 | 下载接管 | `download-resume-dev` | P0；P1 完成后编译联调 | `microservices/sfc-download-service/src/main/java/com/sfc/download/{engine/**,task/**,config/Aria2Properties.java,config/SfcDownloadInternalProperties.java,model/property/Aria2Property.java,util/DownloadFileUtils.java}`、`src/test/java/com/sfc/download/{engine,task,util}/**`、`pom.xml`、`application.yml` |
| P3 | 做种生命周期 | `seed-lifecycle-dev` | P0；与 P2 并行（通过 `P2pSeedRegistrar` SPI 解耦） | 新增 `microservices/sfc-download-service/src/main/java/com/sfc/download/seed/**`、`model/po/DownloadSeedTask.java`、`repo/DownloadSeedTaskRepo.java`、`config/SfcDownloadSchedulingConfig.java`、`DownloadServiceApplication.java`、`service/impl/DownloadServiceImpl.java`、`controller/DownloadController.java`、`model/po/DownloadTaskInfo.java`（仅 transient 字段）、`src/test/java/com/sfc/download/seed/**` |
| P4 | MySQL 数据源 | `download-resume-dev`（P2 后顺序执行） | P2 | `microservices/sfc-download-service/pom.xml`、`src/main/resources/application.yml` |
| P5 | 测试与验收 | `qa-verifier` | P1–P4 | `microservices/sfc-download-service/docs/*verification*`（报告）、`scripts/**`（联调脚本）；只读业务代码 |
| P6 | 文档 | `docs-writer` | P1–P4 | `microservices/sfc-download-service/README.md`、`microservices/sfc-download-service/docs/frontend-adapter-guide.md`、`docs/develop/framework/async-task.md`、`docs/develop/microservice/async-task-worker.md`、本文件定稿 |
| P7 | 集成复核 | Lead | 全部 | 不写业务代码 |

**禁止跨范围写入**；跨范围接口改动先发消息给 Lead。

> **实现状态**：P0–P4 已完成并提交（`1a584056` 框架、`b4638bd8` 下载接管、`e385c20c` 做种生命周期、`24867f14` MySQL）；
> P5 验收（qa-verifier）与 P6 文档（本文件定稿 + 其余 4 份文档）进行中；P7 由 Lead 集成复核。

---

## 7. 测试要求与覆盖

项目规范：禁止 `@SpringBootTest`；使用 JUnit 5 + Mockito 轻量单测（本次用户已明确要求测试）。根 pom 默认 `skipTests=true`，跑测试需 `-DskipTests=false`。

已落地的测试（代码仓库中的 `src/test/java`）：

- **P1**：`SfcTaskExecutionTest`（检查点读写、挂起回调、中断/挂起互斥）、`SfcTaskExecutionRunnerTest`（挂起不报 FINISH、
  异常时挂起优先）、`SfcTaskReporterTest`（检查点补发、detached abandon）、`TaskReportServiceTest`（detached 重新排队不涨重试、
  失联回收按检查点）、`TaskCheckpointServiceTest`（upsert / 删除 / 上限 / 过期）。
- **P2**：`P2pTaskStateTest`（JSON 往返 + 未知版本容错）、`P2pTaskCoordinatorTest`（resume 分支、上传幂等、收口矩阵、
  做种复用）、`Aria2OrphanReconcilerTest`（工作区外不动、终态清理、做种保护跳过、记录不存在不删）、
  `Aria2WorkspaceTest`、`Aria2DownloadEngineSessionTest`、`DownloadTaskHandlerTest`、
  `DownloadFileUtilsProtectedCleanupTest`。
- **P3**：`SeedTaskKeeperTest`（到期清理 / 抢占 / 幂等 / aria2 不可达降级）、`P2pSeedRegistrarImplTest`（登记、0 配置、复用）、
  `SeedHashUtilsTest`（infohash hex/base32、torrent sha256）、`SeedTaskBatchUtilsTest`、
  `DownloadServiceSeedingFillTest`（列表 transient 字段填充）。
- **P4**：SQLite 路径配置绑定 / 启动人工验证；MySQL 路径以配置审查 + 文档验收（实际启动证据见 P5 报告）。
- **P5**：真实 aria2 联调（docker 起 aria2）+ AC 清单 + HTTP/GIT 回归 + 框架回归（core 内嵌 Worker 任务），
  输出 `microservices/sfc-download-service/docs/resume-seed-verification-report.md`。

复跑命令（根目录）：

```bash
mvn -q -pl sfc-api,sfc-core-task -DskipTests=false test
mvn -q -f microservices/pom.xml -pl sfc-task-worker,sfc-download-service -DskipTests=false test
```

---

## 8. 风险与假设

1. **优雅停机窗口**：先挂起、等执行线程退出、再 abandon；`claimedWorker` 校验兜底。SDK 等待窗口为
   `sfc.task.worker.detach-await-seconds`（默认 10 秒）；上传阶段挂起可能导致重复上传（见 3.5 阈值）。
2. **retry_count 耗尽**：有检查点的任务不消耗重试（契约 A 2.2），接管预算由检查点 `resumeCount` 控制。
3. **做种目录被陈旧清理误删**：清理必须排除受保护目录；查询失败则跳过本轮。
4. **跨实例接管**：依赖共享 MySQL + 相同目录映射；SQLite 单实例场景只保证同实例重启接管（跨实例接管不可用，文档明确）。
5. **aria2 重启**：不在保证范围；`gid 丢失 + 目录存在` 时用同目录 `continue=true` 尽力续传。
6. **表结构约束**：`async_task_record` 不动；检查点独立表；任务状态机不新增值。
7. **框架改动影响面**：全部为新增 API/字段 + 新表；旧 handler 与其它微服务零迁移；P5 回归覆盖 core 内嵌 Worker。
8. **上传阶段重复上传**：Worker SDK 无「重新读取 core 检查点」API，接管实例在 `UPLOADING` 新鲜窗口内只能等待，
   超时后重传可能产生重复文件（由 core 落盘侧 `.1` 重名策略兜底）。
9. **同 infohash 复用竞态无专用提示**：提交前 `findReusable` 可避免绝大多数重复任务；极端竞态下仍可能收到
   aria2 原始 `InfoHash is already registered` 错误，未实现可读 409 映射。
10. **做种磁盘占用**：默认保留 7 天，需按「下载量 × 保留时长」规划共享卷容量；`seed-time-minutes=0` 可恢复
    「完成即清理」的旧行为。

---

## 9. 实现差异说明（以代码为准）

> 以下为实现相对初版契约（P0 冻结版）的差异，均已由 Lead 确认；文档与代码按本清单执行。编号 1–10 为
> P2 阶段报告的差异，A–H 为 P6 文档核对代码时补充确认的差异。

1. **detach 检测方式**：任务实现**轮询** `DownloadContext#isDetachRequested()`，保存检查点后抛
   `SfcTaskDetachedException`（不是通过 `onDetach` 回调保存检查点；`onDetach` 仍提供，供实现注册快速动作）。
2. **上传阶段 detach**：允许本次上传完成后写 `UPLOADED` 检查点再退出；接管实例直接补报 FINISH。
3. **上传幂等缺口**：Worker SDK 未暴露「重读检查点」API；`UPLOADING` 新鲜窗口内超时按陈旧重传，可能产生重复文件。
4. **HTTP / GIT / ed2k**：`DownloadTaskHandler#supportsDetach()` 虽恒为 `true`，但这三类无外部可接管现场，
   detach 时按取消语义清理临时目录后重新排队（不写 CANCEL、不消耗重试次数），重新执行时从头下载。
5. **取消并复用他任务做种文件**：不删除目录、不停止 aria2（保护原做种），由做种生命周期负责。
6. **aria2 自身重启**：gid 丢失 + 同目录 `continue=true` 仅尽力续传，不作保证。
7. **SQLite 不保证跨实例接管**：跨实例接管需 MySQL。
8. **OrphanReconciler**：`tellWaiting` / `tellStopped` 单页取 1000 条；下载记录不存在只 WARN 不删除；
   `OFFLINE(5)` 与未知状态保守跳过。
9. **做种时长**只对**新任务**生效；TORRENT 仅给 `.torrent` URL 时无法计算 SHA-256（无复用能力）。
10. **检查点不修改 `async_task_record` 表结构**，新增独立表 `async_task_checkpoint`；执行者主动停机挂起（detached）与
    「带检查点」任务的失联回收重新排队时均不消耗 `retry_count`。
11. **A. 未实现 `InfoHash is already registered` → 409 映射**（见 §8 第 9 条与 4.5）。
12. **B. 停机兜底清扫**：对已收到挂起指令的上下文仍按 detached 语义上报（保留检查点、不消耗重试次数），
    而非初版契约写的「计入重试」；属更保守的正确行为。
13. **C. 实际接口签名**：`P2pTaskCoordinator` 公开方法为
    `loadResumable` / `start` / `resume` / `collectExisting` / `awaitUploadSettled` / `markUploading` / `markUploaded` /
    `finishSuccess` / `releaseOnFailure` / `releaseOnCancel`，返回 `P2pTaskResult(P2pTaskState, List<DownloadArtifact>)`；
    `Aria2TaskSession` 为独立顶层类（由原引擎内部类提升）。
14. **D. `TaskCheckpointService` 实际方法集**：除 `save` / `find` / `delete` / `cleanupExpired` 外新增
    `findExistingTaskIds(Collection<Long>)`（故障转移批量判定）；过期清理由 `@Scheduled` 每日执行（启动 10 分钟后首跑）。
15. **E. 续传 / 上传幂等内部配置**：实际为 `sfc.download.resume.upload-stale-millis`（600000）、
    `sfc.download.resume.upload-wait-interval-millis`（10000）、`sfc.download.resume.upload-wait-attempts`（3），
    初版契约未给出这些 key。
16. **F. 分批工具**：下载服务零宿主依赖，实际使用模块内 `SeedTaskBatchUtils#partition`（行为等价于宿主
    `CollectionUtils#partition`）。
17. **G. 配置面板**：做种字段挂在 `config-properties.json` 的 `download-aria2` 组（标题「aria2 P2P 下载」）下，
    由 `Aria2Property` 的 `typeRef` 自动展开；组路径与既有面板一致。
18. **H. OFFLINE / 未知状态**：孤儿对账对这些状态保守跳过，不清理（初版契约仅列 FINISH / FAILED / CANCEL）。

---

## 10. 验收结论

> **验收报告**：`microservices/sfc-download-service/docs/resume-seed-verification-report.md`（由 P5 / qa-verifier 生成）。
> 截至 P6 定稿时该报告**尚未生成**；生成后在此补充 AC1-1..AC1-7、AC2-1..AC2-6 的逐条结论与证据摘要，
> 并同步更新本文档与 README / 前端适配文档中的验证状态表述。

- 单测：P1–P4 已随代码提交（清单见 §7）；P5 复跑结果以报告为准。
- 真实 aria2 联调与回归（优雅停机不杀 aria2、重启接管不重复下载、离线期间完成后续传上报、取消清理、
  做种到期清理、`seed-time-minutes=0` 旧行为、HTTP/GIT 回归、框架回归）：以 P5 报告为准。
- 已知风险与未覆盖项：见 §8。
