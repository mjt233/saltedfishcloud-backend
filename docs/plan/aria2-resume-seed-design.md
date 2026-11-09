# aria2 下载任务停机续传 + 做种保留 7 天：设计契约（冻结版）

> 状态：**契约冻结（P0）**。本文件是 P1~P6 各阶段实现的唯一契约来源；实现如需偏离，必须先通知 Lead 并更新本文件。
>
> 关键约束（来自需求方）：
> 1. **不得修改 `async_task_record` 的表结构与实体字段**；检查点使用与它通过 `task_id` 关联的**独立新表**。
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
`findByTaskId`、`deleteByTaskId`（`@Modifying`）。

> `async_task_record` 实体、表、DDL 一律不动。`ddl-auto=update` 只负责创建新表。

### 2.2 core 控制面（`sfc-core-task`）

新增 `com.sfc.task.report.TaskCheckpointService`：

| 方法 | 语义 |
|---|---|
| `void save(Long taskId, String taskType, String checkpoint)` | upsert（先查后存即可，SQLite 单写连接 + 短事务）；空 checkpoint 视为删除；超过 1MB 拒绝（记 WARN 并截断/拒绝，实现取拒绝） |
| `String find(Long taskId)` | 无则返回 `null` |
| `void delete(Long taskId)` | 任务进入终态时清理 |
| `int cleanupExpired(Duration maxAge)` | 可选：清理超过 30 天未被更新的残留检查点（每日一次） |

内部协议端点（`TaskWorkerInternalController`）：

| 端点 | 请求 | 响应 | 校验 |
|---|---|---|---|
| `POST /api/internal/task/checkpoint/save` | `TaskCheckpointSaveParam` | `SfcJsonResult<Boolean>` | taskId 存在、status=1(RUNNING)、`claimedWorker == workerId`；否则返回 false（不报错） |

认领下发：`TaskWorkerInternalController#toDispatchInfo(record)` 读取检查点填入 `TaskDispatchInfo.checkpoint`（无则 `null`）。

终态清理（`TaskReportService`）：
- `reportFinish`（FINISH / FAILED / CANCEL）成功后删除检查点；
- `cancelRunningTask`、`failTempTask`、`markFailedIfRetryExceeded` 置终态时删除；
- `reportAbandon`（普通 / detached）与 `reclaimDeadWorkerTasks`（重新排队）**保留**检查点。

**重试计数修正（重要）**：有检查点的任务在「执行者主动停机 / 失联回收」时**不得消耗** `retry_count`，否则服务重启 3 次就会把长下载任务判失败：
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
| `String getCheckpoint()` | 下发时携带的检查点（`saveCheckpoint` 后返回最新写入值） |
| `boolean saveCheckpoint(String checkpoint)` | 同步（带有限重试）上报 core；失败只 WARN 并记为待重发（由 tick 重试），**不向 handler 抛异常** |
| `boolean isDetachRequested()` | 是否收到挂起指令 |
| `void onDetach(Runnable)` | 注册挂起回调；已挂起时立即执行；与 `onInterrupt` 相互独立 |
| package `requestDetach()` | 仅 SDK 调用：置位并触发回调 |

**新异常**：`com.sfc.cloud.task.worker.SfcTaskDetachedException extends RuntimeException`
—— 任务实现检测到挂起后抛出它（或正常返回），SDK 都按「已挂起」处理，**不报 FINISH**。

**`SfcTaskReporter`**

- `reportAbandon(SfcTaskExecution execution, String message)`：内部构造 `TaskAbandonParam(..., message, detached)`，`detached = execution.isDetachRequested()`。
- `saveCheckpoint(SfcTaskExecution execution, String checkpoint)`：带重试的关键上报；失败不抛。
- `tick()`：若执行上下文有待重发检查点，先补发。

**`SfcTaskExecutionRunner`**

- `catch (SfcTaskDetachedException)` → 不写终态，`finally` 中若 `isDetachRequested()` 则 `reporter.reportAbandon(execution, "执行者停机，任务已挂起，等待接管")`，否则才 `reportFinish`。
- 正常返回但 `isDetachRequested()` 为 true → 同样按挂起（abandon）处理。

**`SfcTaskWorkerRuntime.stop()`（改造顺序）**

1. 停止认领线程、停止 tick 调度（现状不变）。
2. 遍历运行中执行上下文：
   - `handler.supportsDetach()` 为 true → `execution.requestDetach()`（**不** requestInterrupt、**不** unregister）；
   - 否则 → 维持现状：`requestInterrupt()` + `unregisterExecution` + `reportAbandon`（普通语义，计重试）。
3. `taskExecutor.shutdown()` + `awaitTermination(detachAwaitSeconds)`；detach 任务应在此窗口内保存检查点并退出（runner 负责上报 abandon(detached)）。
4. await 结束后做最后清扫：仍登记的执行上下文 → `requestInterrupt()` + `unregister` + `reportAbandon`（兜底，计入重试）。
5. 停机窗口内新登记的执行上下文：沿用现有补报逻辑。

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
- 每次 gid 集合变化、阶段变化、detach、上传前后必须 `saveCheckpoint`；轮询期间可不写（避免 RPC 放大），但建议每 60 秒刷新一次 `updatedAt`。
- `resumeCount`：每次「接管」+1；超过 **50** 次视为异常，主动上报 FAILED 并清理（防死循环）。
- `P2pTaskState` 反序列化失败或版本不认识：记 WARN，按「无检查点」处理（重新下载），不抛异常卡死任务。

### 3.2 组件与职责（`engine/p2p`）

| 组件 | 职责 |
|---|---|
| `P2pTaskCoordinator` | P2P 任务总编排：`start`（新建）/`resume`（接管）/阶段收口；不负责上传（上传仍在 `DownloadTaskHandler`） |
| `P2pTaskState` | 检查点模型 + JSON 序列化 |
| `Aria2TaskSession`（可由现有内部类提升） | gid 集合跟踪、元数据→内容 gid 跟随、取消回调 |
| `Aria2OrphanReconciler` | aria2 孤儿任务对账（见 3.6） |
| `P2pSeedRegistrar`（SPI 接口） | 下载完成登记做种、同 infohash 复用查询、目录保护查询；实现由做种阶段（P3）提供，P2 用 `ObjectProvider` 取不到时用 no-op 兜底 |
| `Aria2Workspace`（增强） | 新增：取本服务侧工作区根、判断路径是否在任务工作区内、从目录名解析 downloadId、陈旧清理时跳过受保护目录 |

### 3.3 执行流程（Handler 视角）

```
execute(execution):
  params = parseParams
  context = new DownloadContext(execution, params, nameReporter, tempDirCreator)
  try:
    updateStatus(RUNNING); execution.onInterrupt(context::cancel)
    type = params.type; P2P = type in {MAGNET, TORRENT}
    if P2P:
        state = coordinator.loadResumable(context)          # 读取检查点
        if state != null && state.phase in {SUBMITTED, DOWNLOADING}:
            artifacts = coordinator.resume(state, context)   # 重连或续传
        else if state != null && state.uploaded:
            # 已上传成功但未完成上报：直接补报完成
            finishWithCheckpoint(state); return
        else if state != null && state.phase == UPLOADING:
            # 见 3.5 上传幂等
            artifacts = coordinator.collectExistingArtifacts(state, context)
        else:
            artifacts = coordinator.start(context)           # 新建 + 下载
        # 保存网盘（见 3.5）
    else:
        validateSavePath(context)
        artifacts = engineRegistry.get(type).execute(context)
        stash(...)  # 现状不变
```

- 保存目录校验保持现状（先校验再下载）；接管路径同样先校验。
- 取消语义：`context.checkInterrupted()` 照旧；取消时收口策略见 3.4。
- 日志/进度/任务展示名：沿用现有逻辑；接管时在任务日志输出「已接管既有 aria2 任务：gid=...」。

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
- 移除 `DownloadTaskHandler` 对 P2P 任务目录的无条件删除；只有收口矩阵判定为「删除」时才删。
- 现有 `Aria2DownloadEngine.execute` 的 `finally` 无条件 `forceRemove + removeDownloadResult` **必须改由收口组件执行**。
- 上传阶段被 detach 时**允许上传继续完成**（上传不可中断语义），完成后写 `UPLOADED` 再退出；超过 `detachAwaitSeconds` 由 SDK 兜底中断。

### 3.5 上传阶段幂等

`DownloadTaskHandler` 在上传前后更新检查点：

1. 上传前：`phase=UPLOADING, uploadStartedAt=now` 并保存；
2. 上传成功：`phase=UPLOADED, uploaded=true, savedName, savedBytes` 并保存；
3. 若此时 `execution.isDetachRequested()` → 抛出 `SfcTaskDetachedException`（任务回队列，由接管实例直接补报 FINISH，不再重复上传）。

接管实例遇到 `phase=UPLOADING` 时：
- 若 `now - uploadStartedAt < uploadStaleMillis`（默认 10 分钟）→ 认为旧实例可能仍在上传：最多等待/重读检查点 3 次（每次 ≤ 10 秒）；仍未变成 `UPLOADED` 则按陈旧处理；
- 陈旧 → 重新上传（记 WARN，可能出现重复文件，由 core 落盘侧的重名策略兜底）；
- `phase=UPLOADED` → 直接补报 FINISH（带 `savedName`、`savedBytes`），不重复上传。

### 3.6 aria2 孤儿任务对账（`Aria2OrphanReconciler`）

触发：应用启动完成后一次 + 周期（`sfc.download.aria2.reconcile-interval-millis`，默认 300000，0=关闭）。

安全前提（**必须遵守**）：只处理 `dir` 映射后位于本服务任务工作区（`<sharedDir>/offline-download/` 或 JVM 临时目录 `offline-download/`）下的 aria2 任务；**绝不触碰**工作区之外的 aria2 任务。

对每个候选 gid：
1. 从 `dir` 目录名解析 `<downloadId>-`；
2. 查 `download_task`：
   - `status ∈ {RUNNING(1), WAITING(0)}` → 跳过（可能等待接管）；
   - `status ∈ {FINISH(2), FAILED(3), CANCEL(4)}` 且**不是**做种保护目录 → 清理（`forceRemove` + `removeDownloadResult` + 删除目录）；
   - **记录不存在** → 只记 WARN，不删除（避免误删其它部署/实例的数据）；
3. 对处于做种保护中的目录 → 跳过（由 SeedTaskKeeper 负责）。

需要给 `Aria2Client` 增加：`tellActive(List<String> keys)`、`tellWaiting(int offset, int num, List<String> keys)`、`tellStopped(int offset, int num, List<String> keys)`（aria2 标准 RPC，返回 `List<Map>`）。

### 3.7 配置与选项

- `Aria2Property` 新增 `seedTimeMinutes`（见 4.1）。
- `buildAria2Options`：`seed-time = seedTimeMinutes`（0 表示 `seed-time=0`，保持改造前语义）；新增强制项 `continue=true`（保证同目录续传）；其余不变。
- `Aria2Workspace.cleanupSharedStaleDirsOnce` / `DownloadFileUtils.cleanupStaleTempDirs`：清理前排除「做种保护目录」（通过 `P2pSeedRegistrar.isProtectedDirectory` 查询，查询失败则**跳过本轮清理**，fail-safe）。
- `SfcDownloadInternalProperties` 新增：`aria2.reconcile-interval-millis`（默认 300000）。

---

## 4. 契约 C：做种保留

### 4.1 统一配置（core 面板自动出现）

`Aria2Property` 新增：

```java
@ConfigProperty(
    title = "做种保留时长(分钟)",
    describe = "磁力/种子下载完成后保留本地文件做种分享的时长（分钟）；0=不做种，完成后立即清理",
    defaultValue = "10080")
private Integer seedTimeMinutes = 10080;
```

- key：`download.aria2.seed-time-minutes`；`typeRef` 自动展开到「插件配置 → 离线下载 → aria2 P2P 下载」。
- 取值规则：`null`/`<=0` → 0；上限 525600（1 年），超出按上限并 WARN。
- 生效时机：任务提交给 aria2 时读取快照；修改后仅对**新任务**生效（进行中任务沿用提交时的值），文档注明。

### 4.2 表 `download_seed_task`（下载服务库）

继承 `SfcAuditModel`（雪花主键 + `uid` + 审计字段），`@Table(name = "download_seed_task", indexes = {download_id 唯一, infohash 普通, status+seed_end_at})`：

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
- 启动补扫：`ApplicationReadyEvent` 触发一次。
- 单轮算法：
  1. `findExpired(now, limit=200)`：`status=0 AND seed_end_at <= now`；
  2. 逐条抢占：`UPDATE ... SET status=1 WHERE id=? AND status=0`（多实例幂等，抢不到跳过）；
  3. 清理：`aria2Client.forceRemove(gid)`（若仍 active）→ `removeDownloadResult(gid)` → `DownloadFileUtils.deleteQuietly(localDir)`；
  4. 置 `status=2, finish_at=now, message=...`；
  5. 对 `status=1 且 update_at < now-10min` 的记录（上次清理中途崩溃）重新执行清理（幂等）。
- 批量查询按 `CollectionUtils.partition` 分批（项目规范）。
- aria2 不可达时：只 WARN 并跳过本轮（不置终态），下轮重试；目录删除与 aria2 移除互不阻塞（先移除 aria2，再删目录；aria2 失败也继续删目录并记录 message）。

### 4.4 下载完成登记（`P2pSeedRegistrarImpl`）

- `register(context, state, artifacts)`：
  - `seedTimeMinutes <= 0` → 返回 false（不保留，走「完成即清理」）；
  - 计算 `seedEndAt = now + minutes`；插入 `download_seed_task`（download_id 唯一，重复插入捕获唯一冲突后按已存在处理）；
  - infohash：磁力链从任务参数解析；种子任务从 aria2 状态 `bittorrent.infoHash` 取；种子 SHA-256 从任务参数 `torrentBase64`（或拉取到的字节）计算；
  - 更新检查点 `seeding=true, seedTaskId, seedEndAt`；
  - 返回 true（目录保留）。
- `findReusable(source)`：按 infohash（磁力）/`torrent_sha256`（种子）查 `status=0 AND seed_end_at > now` 且 `local_dir` 存在的记录 → 返回复用目标。
- `isProtectedDirectory(Path dir)`：`status=0` 且 `local_dir` 等于该目录。

### 4.5 同 infohash 复用（避免 `InfoHash is already registered`）

任务开始（P2P 提交前）调用 `findReusable`：

- **命中**：不再提交 aria2，直接把检查点置为 `DOWNLOADED`（`localDir`/`gids` 取自做种记录），走「读取现有文件 → 上传 → FINISH」；任务日志说明「复用做种中的文件，无需重新下载」。做种到期时间不变。
- **未命中**：正常提交；若 aria2 仍返回 `InfoHash is already registered`（极端竞态/无法匹配）→ 清理该重复任务目录并抛出可读业务异常（409）：「该种子正在做种保留中（到期时间 ...），请稍后重试或等待清理」，由用户手动处理。
- 磁力 infohash 解析：`xt=urn:btih:<40hex>` 或 `<32 base32>`（转 hex，小写）；解析失败不阻断任务，只是失去复用能力。

### 4.6 列表展示字段

- `DownloadTaskInfo` 新增 `@Transient` 字段：`Boolean seeding`、`Date seedEndAt`（不落库）。
- 列表/详情接口（`DownloadServiceImpl` 查询后）批量查 `download_seed_task`（按 downloadId 批量，`CollectionUtils.partition`）填充；做种记录查不到 → `seeding=false`。
- 前端适配文档更新：说明两个字段含义（不改前端构建产物）。

---

## 5. 契约 D：下载服务数据源可切换（SQLite / MySQL）

`microservices/sfc-download-service/pom.xml` 新增（版本由 Spring Boot BOM 管理）：
`com.mysql:mysql-connector-j`（runtime）；`org.springframework.boot:spring-boot-starter-test`（test，供单测）。

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
- `ddl-auto=update` 负责建表；**不做数据迁移**（切库即新库），文档写明。
- 多实例接管前提写入 README：MySQL + 相同 `shared-dir/remote-dir` + 相同 `sfc.internal.key` + 同一注册中心。

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

---

## 7. 测试要求

项目规范：禁止 `@SpringBootTest`；使用 JUnit 5 + Mockito 轻量单测（本次用户已明确要求测试）。根 pom 默认 `skipTests=true`，跑测试需 `-DskipTests=false`。

**P1**：`SfcTaskExecution` 检查点读写/挂起回调/互斥；`SfcTaskExecutionRunner` 挂起不报 FINISH；`TaskReportService.reportAbandon(detached)` 走无重试补队（Mockito）；`TaskCheckpointService` upsert/删除。
**P2**：`P2pTaskState` JSON 往返 + 未知版本容错；`P2pTaskCoordinator` 的 resume 三分支（active / complete / gid 丢失）；上传幂等判断（UPLOADING 新鲜/陈旧、UPLOADED 补报）；收口矩阵（成功/取消/失败/挂起）；`Aria2OrphanReconciler` 决策（工作区外不动、终态清理、做种保护跳过）；磁力 gid 跟随写检查点。
**P3**：`SeedTaskKeeper` 到期清理/抢占/幂等/aria2 不可达降级；`P2pSeedRegistrarImpl` 登记、`seedTimeMinutes=0`、复用查询；infohash 解析（hex/base32）与 torrent sha256 匹配；列表 transient 字段填充。
**P4**：配置绑定与启动（SQLite 路径）人工验证；MySQL 路径以配置审查 + 文档验收。
**P5**：真实 aria2 联调（docker 起 `aria2`，如需）+ AC1/AC2 全清单 + HTTP/GIT 回归 + 框架回归（core 内嵌 Worker 任务）。

---

## 8. 风险与假设

1. **优雅停机窗口**：先挂起、等执行线程退出、再 abandon；`claimedWorker` 校验兜底。上传阶段挂起可能导致重复上传（见 3.5 阈值）。
2. **retry_count 耗尽**：有检查点的任务不消耗重试（契约 A 2.2），接管预算由检查点 `resumeCount` 控制。
3. **做种目录被陈旧清理误删**：清理必须排除受保护目录；查询失败则跳过本轮。
4. **跨实例接管**：依赖共享 MySQL + 相同目录映射；SQLite 单实例场景只保证同实例重启接管（跨实例接管不可用，文档明确）。
5. **aria2 重启**：不在保证范围；`gid 丢失 + 目录存在` 时用同目录 `continue=true` 尽力续传。
6. **表结构约束**：`async_task_record` 不动；检查点独立表；任务状态机不新增值。
7. **框架改动影响面**：全部为新增 API/字段 + 新表；旧 handler 与其它微服务零迁移；P5 回归覆盖 core 内嵌 Worker。
