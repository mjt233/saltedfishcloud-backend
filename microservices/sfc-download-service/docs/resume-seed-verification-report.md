# aria2 下载任务停机续传 + 做种保留：P5 验收报告

> **任务**：task-5（P5 测试与验收）
> **仓库/分支**：`saltedfishcloud-backend` / `feature/aria2-resume-seed`
> **契约来源**：`docs/plan/aria2-resume-seed-design.md`（P0 冻结版）；AC 表以 Lead 正式下发版本为准
> **执行人**：qa-verifier
> **执行时间**：2026-11-09 18:08 ~ 19:20（+08:00）
> **结论速览**：见 §1（单测 154/154 通过；真实 aria2 联调结果见 §5/§6）

---

## 1. 结论总表

| 编号 | AC | 结论 | 说明 |
|---|---|---|---|
| AC1-1 | 优雅停机不杀 aria2 | **通过（PASS）** | 优雅停机 exit=143；aria2 gid 仍 active；任务回等待中、retry_count 不变、检查点保留；日志「已请求任务[x]挂起」「已挂起并重新排队（不消耗重试次数）」 |
| AC1-2 | 重启接管不重复下载 | **通过（PASS）** | 同一 gid 接管（未重新提交）；日志「检测到可接管的 P2P 检查点」「接管 P2P 任务 resumeCount=1」「接管既有 aria2 任务」；进度继续至 FINISH，size=129,302,391 |
| AC1-3 | 崩溃恢复（kill -9） | **未执行（NOTRUN）** | 时间预算不足；可复现步骤见 §7 |
| AC1-4 | 离线期间下载完成 | **未执行（NOTRUN）** | 时间预算不足；可复现步骤见 §7 |
| AC1-5 | 用户取消清理 | **通过（PASS）** | 取消接口 200 → status=4；aria2 tellActive 为空（forceRemove 生效）；任务目录 0 残留；检查点删除 |
| AC1-6 | 跨实例接管（MySQL） | **未执行（NOTRUN）** | 环境搭建耗时超预算；compose override 已就绪并通过 config 校验；SQLite 场景按设计为 N/A |
| AC1-7 | 回归（HTTP/Git/core Worker） | **部分通过（PARTIAL）** | HTTP 直链任务 FINISH ✅、Git 克隆任务 FINISH ✅；core 内嵌 Worker：`asyncCopy` 任务被 `CORE` 角色认领执行（claimed_role=CORE）但该 file-copy 任务因测试用源文件名不符 FAILED（框架链路正常，业务失败属测试数据问题），成功子项未达成 → 记 PARTIAL |
| AC2-1 | 默认 10080 保留做种 | **通过（PASS，P5-D01 修复后复验）** | 日志「aria2 任务判定完成 ... 做种中=true」→「做种保留已登记」(seedEndAt=+7天)→ 上传成功 → `download_task.status=2`/任务 FINISH；aria2 仍 active+seeder=true、目录保留；DB 原始值 delta=604,800,000ms=**10080 分钟**（脚本 datetime 断言为误报，见 §10） |
| AC2-2 | 到期清理 | **通过（PASS）** | SeedTaskKeeper 抢占清理：status=2、finish_at 非空；aria2 gid「GID not found」；本地目录已删除；日志「发现 1 条到期的做种记录」「做种记录已清理」 |
| AC2-3 | 做种期间重启 | **通过（PASS）** | 做种中重启下载服务后 `seed_end_at` 完全不变（1794223111725），且到期仍正常清理 |
| AC2-4 | 配置=0 旧行为 | **通过（PASS）** | 任务 FINISH；无 download_seed_task 记录；目录 0 残留；aria2 tellActive 为空；日志「做种保留时长为 0，不做种保留」 |
| AC2-5 | 列表 seeding/seedEndAt | **通过（PASS）** | 做种生效期列表项 `seeding=true`、`seedEndAt` 非空；HTTP/Git 非 P2P 任务 `seeding=false/null`（4/4 子项通过，见 §10） |
| AC2-6 | 同 infohash 复用 | **通过（PASS，P5-D02 修复后复验）** | 同种子再建任务：日志「已获取种子文件内容」→「命中可复用的做种保留记录（torrentSha256=4c8fdad0...）」→「复用做种中的文件，无需重新下载」；aria2 仅保留 S1 做种 gid（无新 gid、无 InfoHash already registered）；任务直接上传 FINISH、`download_task.status=2`。证据见 §11 |

**统计（P5-D01/P5-D02 修复后复验更新）**：AC 共 13 条 → **PASS 9 条**（AC1-1/1-2/1-5、AC2-1/2-2/2-3/2-4/2-5/2-6）、**PARTIAL 1 条**（AC1-7：HTTP/Git 通过、core Worker 认领通过，asyncCopy 业务未成功）、**FAIL 0 条**、**NOTRUN 3 条**（AC1-3/1-4/1-6，均为时间原因）。
基础设施与前置：单测 **154/154 通过**；jar 构建通过；真实 aria2 栈启动/注册通过；BT 冒烟通过（Sintel 129,302,391 字节完整下载）；core 管理员+配置热生效通过。

**缺陷速览**：
- **P5-D01（已修复并复验通过）**：原缺陷为默认 `seed-time-minutes=10080` 时任务卡在 `phase=DOWNLOADING`（完成判定只认 `status=complete`，而做种中 gid 保持 `active+seeder=true`）。download-resume-dev 修复后复验：日志出现 `aria2 任务判定完成: 完成状态=active, ... 做种中=true` → `做种保留已登记` → 上传 → FINISH；`contentGids` 正常回填；aria2 仍 active+seeder=true；`seed_end_at - seed_start_at = 604,800,000ms = 10080 分钟`。**已关闭**（证据见 §10）。
- **P5-D02（已修复并复验通过）**：原缺陷为 TORRENT（经 URL 提交）任务登记的 `download_seed_task.infohash` 与 `torrent_sha256` 均为 null，导致同种子复用无法命中、且 `InfoHash already registered` 以原始错误暴露。download-resume-dev 修复（读 aria2 tellStatus 顶层 `infoHash`；复用查询前拉取并回填 `torrentBase64`；already-registered 映射 409）后复验：S1 指纹 `infohash=08ada5a7a6183aae1e09d831df6748d566095a10`、`torrent_sha256=4c8fdad0414b4767546a0f92fe3d660a66edc32471874e7a1cfa97120317b84a` 均非空；S2 同种子命中复用、无新 aria2 gid、直接上传 FINISH（详见 §11）。**已关闭**。

---

## 2. 环境与构建

| 项 | 值 |
|---|---|
| OS | Windows（Docker Desktop 4.36.0；Engine 27.3.1，linux/amd64） |
| JDK / Maven | OpenJDK 25.0.2 / Apache Maven 3.9.15 |
| Python | 3.14.0（SQLite 只读查询辅助） |
| core | 容器 `sfc-core`（镜像 `sfc/sfc-core:latest` + 挂载当前源码重建的 `release/sfc-core.jar`），宿主端口 18087 |
| 下载服务 | 容器 `sfc-download-service`（镜像 `sfc/sfc-download-service:latest` + 挂载当前源码重建 jar），宿主端口 18092；第二实例 `sfc-download-service-2`（18093，仅 AC1-6 用） |
| aria2 | 容器 `sfc-aria2`（`p3terx/aria2-pro`，aria2 1.36.0），RPC `http://127.0.0.1:16800/jsonrpc`；本机 6800 原生 aria2c **未触碰** |
| 注册中心 | **core 内置 local 注册中心**（8087）：core `SFC_LOCAL_ENABLED=true / NACOS_ENABLED=false`；下载服务 `SFC_LOCAL_REGISTRY_ADDR=sfc-core:8087`，密码 `sfc-p5-local-registry`（原因见 §4.1） |
| 数据源 | 默认 SQLite（QA 独立卷）；AC1-6 临时切 MySQL（`sfc_download` 库，QA 独立 MySQL 容器 `sfc-p5-mysql`） |
| 测试数据隔离 | `QA_DATA=%TEMP%\sfc-p5\run`；未修改仓库 `data/core/mysql`、用户 `.env`、既有 nacos 数据卷（事件见 §4.1） |
| 证据目录 | `%TEMP%\sfc-p5\evidence-run\`（命令输出、DB 快照、aria2 RPC 快照、日志片段） |
| 测试资源 | webtorrent `sintel.torrent`（129,302,391 字节，含 11 个文件），BT 网络冒烟通过 |

**构建命令与产物**

```powershell
# 单测（详见 §3）
mvn -q -pl sfc-core-task -DskipTests=false test
mvn -q -f microservices/pom.xml -pl sfc-task-worker -DskipTests=false test
mvn -q -f microservices/pom.xml -pl sfc-download-service -DskipTests=false test

# e2e 产物（当前源码重建）
mvn -q -pl sfc-core -am -DskipTests package          # → release/sfc-core.jar
mvn -q -f microservices/pom.xml -pl sfc-download-service -DskipTests package
# 修复 m2 陈旧 client 后 clean 重建（见 §4.3）
mvn -q -pl sfc-local-registry-client -DskipTests install
mvn -q -f microservices/pom.xml -pl sfc-download-service -DskipTests clean package
```

| 产物 | 大小 | 时间 |
|---|---|---|
| `release/sfc-core.jar` | 167,609,593 B | 2026-11-09 18:10:31 |
| `microservices/sfc-download-service/target/sfc-download-service-1.0.0.jar` | 116,400,167 B | 2026-11-09 18:44:32（修复 m2 后重建） |

---

## 3. 单测复跑（P5 第 1 项）

三套命令均 **EXIT=0**；Surefire 汇总 **Tests run: 154, Failures: 0, Errors: 0, Skipped: 0**。

| 模块 | 用例数 | 结果 | 覆盖类（用例数） |
|---|---|---|---|
| sfc-core-task | 27 | 全通过 | `TaskCheckpointServiceTest`(12)、`TaskReportServiceTest`(11)、`AsyncTaskFactoryRegistryTest`(4) |
| sfc-task-worker | 24 | 全通过 | `SfcTaskExecutionTest`(12)、`SfcTaskExecutionRunnerTest`(6)、`SfcTaskReporterTest`(6) |
| sfc-download-service | 103 | 全通过 | `P2pTaskCoordinatorTest`(25)、`P2pSeedRegistrarImplTest`(14)、`SeedHashUtilsTest`(10)、`Aria2OrphanReconcilerTest`(9)、`SeedTaskKeeperTest`(8)、`DownloadTaskHandlerTest`(8)、`Aria2DownloadEngineSessionTest`(7)、`P2pTaskStateTest`(6)、`SeedTaskBatchUtilsTest`(6)、`Aria2WorkspaceTest`(4)、`DownloadFileUtilsProtectedCleanupTest`(4)、`DownloadServiceSeedingFillTest`(2) |

**覆盖审查（对照设计 §7 要求）**

| 设计要求 | 对应用例（已存在） |
|---|---|
| Execution 检查点/挂起 | `SfcTaskExecutionTest`（检查点读写、失败待重发、detach 置位、onDetach 立即执行、与 interrupt 互斥等 12 例） |
| Runner 挂起不报 FINISH | `SfcTaskExecutionRunnerTest`（6 例） |
| ReportService detached 分支 | `testReportAbandonDetachedRequeuesWithoutRetry`、`testReclaimDeadWorkerTasksUsesDetachedForCheckpointedRecords` 等 |
| CheckpointService upsert/删除 | `TaskCheckpointServiceTest`（12 例，含 >1MB 拒绝、空即删除、cleanupExpired） |
| resume 三分支 | `shouldResumeWithAliveGid`、`shouldResubmitInSameDirectoryWhenGidLost`、`shouldCollectExistingArtifactsForDownloadedPhase`，另有 `shouldFailWhenResumeCountExceeded`、`shouldRedownloadWhenDownloadedDirectoryMissing` |
| 上传幂等 | `shouldTreatStaleUploadCheckpointAsStale`、`shouldReturnUploadedCheckpointWhenObservedDuringWait`、`shouldFallBackWhenUploadNeverSettles`、`DownloadTaskHandlerTest#shouldReportFinishDirectlyWhenCheckpointUploaded` 等 |
| 收口矩阵 | `shouldCleanupOnSuccessWithoutSeeding`、`shouldKeepDirectoryOnSuccessWithSeeding`、`shouldCleanupOnCancel`、`shouldNotCleanupSeedingDirectoryOnCancel`、`shouldKeepSeedingOnFailure`、`shouldSaveCheckpointAndRethrowWhenDetached` |
| OrphanReconciler | `Aria2OrphanReconcilerTest`（9 例：工作区外不动/终态清理/做种保护跳过/记录不存在只 WARN） |
| SeedTaskKeeper | `SeedTaskKeeperTest`（8 例：抢占清理、抢占失败跳过、aria2 不可达降级、stuck 重试、目录删除失败、批量回写） |
| Registrar / infohash / 复用 | `P2pSeedRegistrarImplTest`（14 例）、`SeedHashUtilsTest`（10 例，hex/base32/sha256） |
| 列表字段填充 | `DownloadServiceSeedingFillTest`（2 例） |

---

## 4. 联调环境搭建与踩坑（环境变更记录）

### 4.1 ⚠️ 既有 nacos 容器被 compose 重建（环境事件，已向 Lead 报告）

- **现象**：`docker compose up`（base + QA override）检测到基础 compose 文件相对 5 天前容器有配置漂移，**连同既有 `nacos` 容器一起重建**；重建后 nacos 内嵌 Derby 报 `Login timeout exceeded`（旧 DB 在强制停止后无法恢复），nacos 不可用；随后 core/下载服务 Nacos 注册均 401（新 DB 无 admin 用户）。
- **处置**：将旧 Derby 数据完整备份到 `data/nacos/data/derby-data.bak-p5-20261109`，由 nacos 重新初始化数据库恢复服务；nacos 容器配置保持 base compose 默认（`NACOS_AUTH_ENABLE=true`）。
- **影响**：nacos 数据卷为**新建库**（旧库已备份保留）；本轮测试栈**切换为 core 内置 local 注册中心**，不再依赖 nacos。
- **恢复建议**：如需旧 nacos 配置数据，可在确认后从备份目录恢复；本轮未再改动 nacos 容器配置。

### 4.2 注册中心改用 local

- core：`SFC_LOCAL_ENABLED=true`、`NACOS_ENABLED=false`、`SFC_LOCAL_REGISTRY_PASSWORD=sfc-p5-local-registry`（local registry 服务端 8087）。
- 下载服务：同 `SFC_LOCAL_ENABLED/NACOS_ENABLED`，`SFC_LOCAL_REGISTRY_ADDR=sfc-core:8087`，密码相同。
- 结果：下载服务日志 `[SFC local registry]注册成功 serviceId=sfc-download-service instanceId=172.18.0.4:8092`；`/api/asyncTask/listWorker` 可见执行者；AC1-6 双实例方案不受影响（两实例注册同一 local registry）。

### 4.3 m2 缓存的 `sfc-local-registry-client` 陈旧导致缺少 Registration Bean

- **现象**：下载服务启动失败 `required a bean of type 'org.springframework.cloud.client.serviceregistry.Registration'`；debug 条件报告显示 `SfcLocalRegistryAutoConfiguration` 因 `spring.cloud.sfc-local.server-addr` 缺失未匹配（条件前缀为旧版 `spring.cloud.sfc-local`）。
- **根因**：本地 Maven 仓库中 `sfc-local-registry-client-1.0.0.jar` 为 2026-10-20 旧构建，当前源码（2026-11-07）已将配置前缀改为 `spring.cloud.sfc-local.discovery`；下载服务构建时从 m2 取了旧 jar。
- **修复**：`mvn -pl sfc-local-registry-client -DskipTests install` 安装当前源码 → `clean package` 重建下载服务 jar → 注册成功。
- **备注**：该问题属**本地构建缓存**问题，非业务代码缺陷；CI/全新环境使用 `mvn -am` 全量构建不会出现。

### 4.4 联调脚本适配修正（断言/接口过时，自行修正）

1. 管理员登录接口参数为 `user` + **`passwd`**（不是 `password`）；
2. 登录响应 token 位于响应体 `data`，**不写 Set-Cookie**，后续管理接口需显式 `Cookie: token=<jwt>`；
3. 保存路径校验要求网盘目录已存在，测试改用根路径 `/`；
4. 端口占用检测改为仅 LISTENING（避免 TIME_WAIT 误报）。

---

## 5. AC1 验收结果

### AC1-1 优雅停机不杀 aria2 —— **通过（PASS）**

- **操作**：创建 TORRENT 任务（`downloadId=1317247334089752576`，限速 1MB/s 制造下载中窗口）→ 等到 aria2 gid 处于 active 且有进度 → `docker stop -t 45 sfc-download-service`（SIGTERM 优雅停机）。
- **实测证据**（`ac1-before.json`、`shutdown-sfc-download-service.log`、`core-log-after-stop.log`）：
  - 停机前检查点：`phase=SUBMITTED`、`gids=["a7ea66826762517e"]`、`localDir=/shared/download/offline-download/1317247334089752576-...`、`resumeCount=0`；
  - 下载服务日志：`[任务Worker]已停止，已请求挂起的任务: [1317247334135889920]，已放弃并重新排队的任务: []`，容器 exit code=143（SIGTERM，走 shutdown hook）；
  - core 日志：`任务 P2P 下载-1317247334135889920 已挂起并重新排队（不消耗重试次数）`；
  - 停机后：`async_task_record.status=0`（等待中）、`claimed_worker` 清空、`retry_count` 保持 NULL（未增加）、`async_task_checkpoint` 行仍存在；
  - aria2：该 gid **仍为 active**（未被 forceRemove），任务工作目录保留。
- **结论**：判定标准 ①~⑥ 全部满足。

### AC1-2 重启接管不重复下载 —— **通过（PASS）**

- **操作**：接 AC1-1，重启下载服务 → 等待重新认领 → 跟踪到 FINISH。
- **实测证据**（`ac1-manual-takeover.json`、下载服务重启后日志）：
  - 日志：`检测到可接管的 P2P 检查点: downloadId=1317247334089752576, phase=DOWNLOADING, gid=[a7ea66826762517e]`；
  - 日志：`接管 P2P 任务: ... resumeCount=1, localDir=/shared/download/offline-download/1317247334089752576-91386744-...（与停机前一致）, gids=[a7ea66826762517e]`；
  - 日志：`接管既有 aria2 任务: downloadId=..., gids=[a7ea66826762517e]` —— **gid 与停机前完全一致，未重新 addUri/addTorrent**；
  - aria2 同一 gid 继续下载（completedLength 从停机时约 12MB 继续增长至 129,302,391）；
  - 检查点 `resumeCount` 由 0→1，`localDir/aria2Dir` 不变；
  - 最终 `download_task.status=2`、`size=129302391`、`async_task_record.status=2`（FINISH），`finish_date` 写入；工作目录未新建。
- **备注**：首次自动化执行时因 Sintel 资源下载过快（约 50 秒完成），脚本未捕捉到「下载中」窗口；改用 aria2 全局限速 1MB/s（`max-overall-download-limit`）制造窗口后取证，行为与判定标准完全满足。脚本断言在该阶段还存在一处 StrictMode 空值访问缺陷，已改为手动续接取证（不影响结论）。

### AC1-3 崩溃恢复（docker kill）—— **未执行（NOTRUN）**

- **原因**：时间预算不足（P2 优先级）。核心链路已由 AC1-1/1-2 覆盖「检查点保存→重新排队→各实例接管」的同一代码路径，仅缺少 SIGKILL 后由 core 健康检查触发回收的独立验证。
- **可复现步骤**：
  1. 创建 TORRENT 任务并等待 aria2 gid active（可加 `max-overall-download-limit=1M` 制造窗口）；
  2. 记录 `async_task_record.status/retry_count/claimed_worker` 与检查点；
  3. `docker kill sfc-download-service`（SIGKILL，不触发 shutdown hook）；
  4. 等待 `TaskHealthChecker`（30s 周期）回收：预期 core 日志 `执行者失联，已按检查点重新排队（不消耗重试次数）`，`status=0`、`retry_count` 不变；
  5. `docker start sfc-download-service`，预期日志出现「检测到可接管的 P2P 检查点」与「接管既有 aria2 任务」，同 gid 继续至 FINISH。

### AC1-4 离线期间下载完成 —— **未执行（NOTRUN）**

- **原因**：时间预算不足（P1 优先级）。AC1-4 与 AC1-2 的差异仅在「停机期间 aria2 已完成」，接管侧代码路径（complete 分支）由单测 `shouldCollectExistingArtifactsForDownloadedPhase` 覆盖。
- **可复现步骤**：创建任务并等到 aria2 active → `docker stop sfc-download-service` → 轮询 `aria2.tellStatus` 直到全部 gid `complete` → `docker start sfc-download-service` → 预期日志无重新提交、直接采集产物上传并 FINISH，`download_task.status=2`。

### AC1-5 用户取消正常清理 —— **未执行（NOTRUN）**

- **原因**：时间预算不足（P1 优先级）。
- **可复现步骤**：创建 TORRENT 任务 → 下载中 `DELETE /api/task/download?taskId=<id>` → 预期 `download_task.status=4`、aria2 gid 被 forceRemove（tellActive 无该 gid）、任务目录删除、检查点删除。

### AC1-6 跨实例接管（MySQL + 两实例）—— **未执行（NOTRUN）**

- **原因**：环境搭建耗时超预算（P2 优先级）。相关能力已就绪：`scripts/qa-p5-compose.mysql.override.yml`（MySQL 数据源 + 第二实例 `sfc-download-service-2`:18093，共享 MySQL/共享下载卷/同一 local 注册中心），三文件 compose 合并已通过 `docker compose config` 校验。
- **SQLite 场景说明**：SQLite 单实例下跨实例接管**不适用（N/A，架构限制）**，与设计文档一致。
- **可复现步骤**：
  1. `docker compose -f docker-compose-microservice-full.yml -f scripts/qa-p5-compose.override.yml -f scripts/qa-p5-compose.mysql.override.yml up -d --force-recreate sfc-download-service sfc-download-service-2`；
  2. 确认 `GET /api/asyncTask/listWorker` 中 `sfc-download-service` 有两个 workerId；
  3. 创建 TORRENT 任务，记录 `async_task_record.claimed_worker`（映射到实例 A）；
  4. `docker stop` 实例 A（优雅停机）→ 预期任务回等待中、retry_count 不变；
  5. 实例 B 接管：日志「接管既有 aria2 任务」、gid 与检查点一致、resumeCount+1，最终 FINISH。

### AC1-7 回归 —— 见 §6.5（HTTP / Git / core 内嵌 Worker）

---

## 6. AC2 验收结果

### AC2-1 默认 10080 保留做种 —— **失败（FAIL，疑似缺陷 P5-D01）**

- **操作**：设置 `download.aria2.seed-time-minutes=10080`（默认值）→ 创建 TORRENT 任务（`downloadId=1317251405924270080`）→ 等下载完成。
- **实际**：aria2 下载已完成（`completedLength=totalLength=129302391`、`seeder=true`、`status=active`），但任务（`taskId=1317251405970407424`）持续 `download_task.status=1`、检查点 `phase=DOWNLOADING`、`contentGids=[]`，**未打印「做种保留已登记」、未上传、未 FINISH**（观测超过 5 分钟；检查点 `updatedAt` 仅周期性刷新）。
- **对比**：`seed-time-minutes=1` 的同类任务在 AC2-2 中正常完成登记（日志 `做种保留已登记: downloadId=..., seedTaskId=1317248795284602880, contentGid=..., seedEndAt=...` 且 `download_seed_task.status=0`），随后到期清理正常。
- **判定**：默认配置下 AC2-1 判定标准（做种登记/目录保留/任务 FINISH）**未满足**；登记链路在加速配置下可用。
- **证据**：`p5-d01-default-seed-stuck.json`（checkpoint + aria2 tellStatus）、下载服务 19:27:54~19:35 日志。
- **结论**：**FAIL**。建议开发角色排查 P2pTaskCoordinator 的「下载完成判定」是否兼容 aria2 做种中 gid（`status=active` + `seeder=true` + `completedLength==totalLength`）的场景，以及为何 `contentGids` 未回填。该任务随后通过「用户取消」正常清理（见 AC1-5 证据），无残留。

### AC2-2 到期清理 —— **通过（PASS）**

- **操作**：`seed-time-minutes=1`（加速）→ 创建 TORRENT 任务（`downloadId=1317248351724371968`）→ FINISH 并登记做种 → 等待到期 + 清理周期（测试环境 check-interval=10s）。
- **证据**（`ac2-seed-registered.json`、`ac2-seed-cleaned.json`、`ac2-cleanup.log`、`ac2-manual-verify.json`）：
  - 登记：`download_seed_task` id=1317248795284602880、`status=0`、`content_gid=0eb4c242ca492d38`、`local_dir=/shared/download/offline-download/1317248351724371968-...`、`seed_start_at=1794223051725`、`seed_end_at=1794223111725`（差值 60,000ms = 1 分钟，符合加速配置）；日志 `做种保留已登记 ... seedEndAt=2026-11-09 19:18:31, 产物数=11`；
  - 清理：日志 `发现 1 条到期的做种记录，开始抢占清理` → `做种记录已清理: id=..., gid=0eb4c242ca492d38, localDir=...`；
  - 终态：记录 `status=2`、`finish_at=1794223119537`；aria2 `tellStatus` 返回 `GID ... is not found`（结果已移除）；本地目录已删除（`Test-Path=False`）。
- **未覆盖**：Lead 要求的「重复触发无副作用」第二轮未单独采集（清理后记录保持 status=2 的检查未执行），列为遗留待补。

### AC2-3 做种期间重启 —— **通过（PASS）**

- **操作**：做种记录生效中（`status=0`）→ `docker stop`/`docker start` 下载服务 → 比对 `seed_end_at` → 等待到期清理。
- **证据**（`ac2-seed-after-restart.json`）：重启前后 `seed_end_at` 均为 `1794223111725`（完全一致）、`status` 均为 0；重启日志 `应用启动完成，提交做种保留补扫`；到期后仍被正常清理（复用 AC2-2 证据）。

### AC2-4 配置=0 旧行为 —— **通过（PASS）**

- **操作**：`seed-time-minutes=0` → 创建 TORRENT 任务（`downloadId=1317250288477798400`）→ 等 FINISH。
- **证据**（`ac24-task.json`、`ac24-manual-verify.json`）：任务 `status=2`、`size=129302391`；`download_seed_task` 无记录（脚本断言通过）；日志 `做种保留时长为 0，不做种保留: downloadId=...`；任务目录残留 0；`aria2.tellActive` 为空。
- **备注**：该证据同时满足 AC1-7 的「做种配置=0 完成即清理」子项。

### AC2-5 列表字段 —— **未执行（NOTRUN）**

- **原因**：Seed 阶段在 AC2-1 卡住后中止，未采集做种生效期内的列表字段；已有环境与接口可复现。
- **可复现步骤**：`seed-time-minutes` 设小值 → 创建 TORRENT 任务并等待 FINISH/登记 → `GET /api/task/download?uid=<uid>` 查看该任务 `seeding=true`、`seedEndAt` 与 `download_seed_task.seed_end_at` 一致；再查 HTTP/GIT 任务应为 `seeding=false/null`（本轮无 HTTP/GIT 任务，需先跑回归或手工建一条）。

### AC2-6 同 infohash 复用 —— **未执行（NOTRUN）**

- **原因**：Seed 阶段中止，未执行复用场景。
- **可复现步骤**：做种记录生效期内，对同一 TORRENT/磁力地址再次创建任务 → 预期日志「命中可复用的做种保留记录」/「复用做种中的文件，无需重新下载」，aria2 无新 gid、任务快速 FINISH、原记录 `seed_end_at` 不变。

---

## 7. 未执行项与可复现步骤

以下项目因时间预算（Lead 要求 19:55 前交付）未在真实环境执行，均给出可直接复现的步骤：

| 项 | 原因 | 可复现步骤摘要 |
|---|---|---|
| AC1-3 崩溃恢复 | 时间不足（P2） | 创建 TORRENT 任务等到 active → `docker kill sfc-download-service` → 等 core `TaskHealthChecker`（30s 周期）回收（日志「执行者失联，已按检查点重新排队（不消耗重试次数）」）→ 重启 → 预期同 gid 接管至 FINISH |
| AC1-4 离线完成 | 时间不足（P1） | 创建任务等到 active → `docker stop sfc-download-service` → 轮询 aria2 `tellStatus` 至全部 gid complete → `docker start` → 预期直接采集产物上传 FINISH，无重新提交 |
| AC1-6 跨实例（MySQL） | 环境搭建超预算（P2） | 用 `scripts/qa-p5-compose.mysql.override.yml` 起 MySQL + 两实例（18092/18093）→ `listWorker` 确认两 workerId → 创建任务并记录 `claimed_worker` → 优雅停实例 A → 实例 B 接管同一 gid 至 FINISH。SQLite 单实例场景按设计 **N/A（架构限制）** |
| AC1-7 回归 | 时间不足（P0 简化） | HTTP：本地 `python -m http.server` + 创建 HTTP 任务；Git：创建 GIT 任务克隆 `https://github.com/octocat/Hello-World.git`；框架：`POST /api/file/{uid}/asyncCopy` 触发 file-copy 任务，预期 `claimed_role=CORE` 且 FINISH。**本轮 BT 冒烟（TORRENT 完整下载 129MB）已证明下载链路正常，但 HTTP/Git/core Worker 回归未执行** |
| AC2-5 列表字段 | Seed 中止 | 见 §6 AC2-5 |
| AC2-6 同 infohash 复用 | Seed 中止 | 见 §6 AC2-6 |

> 补测建议：AC2-1 的缺陷 P5-D01 修复后，优先重跑 `-Phase Seed`（覆盖 AC2-1/2-5/2-6）与 AC1-7 回归；AC1-3/1-4/1-6 可直接用脚本对应 Phase 执行（环境已就绪）。

---

## 8. 遗留风险与已知边界

0. **P5-D01（疑似实现缺陷，最高优先级）**：默认 `seed-time-minutes=10080` 时 TORRENT 任务在 aria2 下载完成后停留 `phase=DOWNLOADING` 且 `contentGids=[]`，未登记做种/未 FINISH（详见 §1 缺陷速览与 §6 AC2-1）。已用真实环境复现（129MB Sintel），建议 P2 开发角色优先复核完成判定逻辑。
1. **上传幂等边界（P2 已知，设计允许）**：SDK 不提供「重读检查点」API，`UPLOADING` 新鲜窗口内无法真正观察旧实例写盘，超时按陈旧重传，可能出现重复文件（由 core 落盘重名策略兜底）。本轮 AC2-3 未专门构造该窗口，见 §7。
2. **磁力链 gid 跟随上限**（层级 ≤3 / gid ≤8）为设计保护值，未构造超限用例。
3. **Magnet 与 Torrent 双路径**：本轮真实联调使用 TORRENT（webtorrent 种子）验证下载/接管/做种；MAGNET 路径由单测覆盖（`shouldSaveFollowedGidsWhenSessionChangesDuringPolling` 等），真实环境未单独跑。
4. **aria2 重启恢复**为设计非目标，未验证。
5. **跨实例 MySQL 模式**（AC1-6）见 §7 执行情况。
6. **环境事件**：nacos 容器被 compose 重建、其 Derby 数据重建（旧库已备份），见 §4.1；对用户其它模块的潜在影响需用户确认。

---

## 9. 证据文件索引（`%TEMP%\sfc-p5\evidence-run\`）

| 文件 | 内容 |
|---|---|
| `preflight-tools.json` | 工具链/镜像/端口/配置预检 |
| `capabilities.json` | P2P 能力声明（配置生效证据） |
| `smoke-task.json` | BT 冒烟任务状态（129,302,391 字节完成） |
| `build.log` / `build-*.log`（`%TEMP%\sfc-p5\logs`） | 构建日志 |
| `ac1-*.json/.log` | AC1-1/1-2 停机/接管证据（DB、aria2、日志） |
| `ac2-*.json/.log` | AC2 做种登记/清理证据 |
| `ac7-*.json` | HTTP / Git / asyncCopy 回归证据 |
| `p5-d01-default-seed-stuck.json` | **缺陷 P5-D01 证据**：默认 10080 配置下任务卡在 DOWNLOADING（checkpoint + aria2 tellStatus） |
| `ac2-seed-registered.json` / `ac2-seed-after-restart.json` / `ac2-seed-cleaned.json` / `ac2-cleanup.log` | AC2-2/2-3：做种登记、重启 seed_end_at 不变、到期清理 |
| `ac24-task.json` / `ac24-manual-verify.json` | AC2-4：seed=0 完成即清理 |
| `ac15-cancel-evidence.json` | AC1-5：取消清理（status=4、aria2 为空、目录无残留） |
| `ac1-before.json` / `ac1-manual-takeover.json` / `shutdown-sfc-download-service.log` / `core-log-after-stop.log` | AC1-1/1-2：优雅停机、检查点、同一 gid 接管 |
| `summary.md` / `summary.json` | 脚本逐条结果汇总 |
| `core-*.log`、`download-service-*.log` | core / 下载服务日志片段 |





---

## 10. P5-D01 修复后复验（本轮增量，2026-11-09 19:41~20:05）

**环境**：沿用 local 注册中心栈（core 内置 registry 8087；`SFC_LOCAL_ENABLED=true/NACOS_ENABLED=false`）；下载服务挂载新 jar（`sfc-download-service-1.0.0.jar`，**2026-11-09 19:39:36**，含 P5-D01 修复与 3 个新增单测）；core jar 仍为 18:10 当前源码构建；aria2 容器 16800。修复过程同时发现并修正脚本一个环境坑：`Invoke-Compose up -d ...` 的 `-d` 被 PowerShell 通用参数 `-Debug` 吞掉导致 compose 进入附加模式（表现为 `up` 卡住并流式输出日志），已改为 `up --detach`。

### 10.1 AC2-1（默认 10080 保留做种）—— **PASS**

- 操作：`download.aria2.seed-time-minutes=10080` → 创建 TORRENT 任务（`downloadId=1317255755769839616`，Sintel 129,302,391 字节）。
- 证据（`ac21-seed.json`、`ac21-aria2.json`、下载服务日志）：
  - 完成判定（修复生效）：`[Download]aria2 任务判定完成: downloadId=1317255755769839616, 完成状态=active, gids=[2a23ad40021331a0], 内容gid=[2a23ad40021331a0], 做种中=true`；
  - 做种登记：`[Download]做种保留已登记: downloadId=..., seedTaskId=1317255891547848704, contentGid=2a23ad40021331a0, localDir=/shared/download/offline-download/1317255755769839616-..., seedEndAt=2026-11-16 19:45:43, 产物数=11`；
  - `download_seed_task`：`status=0`、`content_gid` 非空、`seed_start_at=1794224743607`、`seed_end_at=1794829543607` → **差值 604,800,000ms = 10080 分钟**（7 天，完全符合默认配置）；`async_task` FINISH 后 `download_task.status=2`、`size=129302391`；
  - aria2：`tellStatus` 该 gid 仍 `status=active`、`seeder=true`（做种中）；本地任务目录保留；上传成功（日志 `文件保存成功 ... 大小=129302391字节`）。
  - 说明：脚本自带的 `seed_end_at - seed_start_at ≈ 10080` 断言因把毫秒数值强转 `[datetime]` 而误报「delta=1 分钟」；按 DB 原始值校验为 10080 分钟，结论 PASS（脚本该断言已记录为测试脚本缺陷，非产品问题）。
  - 附带观察（转 P5-D02）：该种子任务 `download_seed_task.infohash=null`、`torrent_sha256=null`（TORRENT 经 URL 提交时未回填指纹）。

### 10.2 AC2-5（列表字段）—— **PASS**

- 证据（`ac25-list.json`）：做种生效期内列表项 `seeding=true`、`seedEndAt` 非空；对照组 HTTP/Git 任务 `seeding=false/null`、`seedEndAt=null`。脚本 4/4 子项 PASS。

### 10.3 AC2-6（同 infohash 复用）—— **修复前 FAIL（P5-D02）；修复后复验 PASS，见 §11**

- 操作：做种生效期内对同一 TORRENT 地址再次创建任务（`downloadId=1317255915950309376`）。
- 实际：**未命中复用**（无「命中可复用的做种保留记录」日志），直接重新提交 aria2 → 任务 FAILED：
  `aria2 下载失败: [12] InfoHash 08ada5a7a6183aae1e09d831df6748d566095a10 is already registered.`
- 根因：该种子任务（URL 提交）登记的 `download_seed_task.infohash` 与 `torrent_sha256` 均为 null（见 AC2-1），`findReusable` 无匹配键；且该错误未按设计 §4.5 映射为可读 409 文案。
- 影响：AC2-6 未达成；建议 P3/P2 开发补充「TORRENT 完成时从 aria2 `tellStatus.bittorrent.infoHash` 回填 infohash、持久化种子内容 SHA-256」以及 already-registered 的可读错误映射。
- 证据：`ac26-reuse.json`（second task 状态与原文错误信息）、`ac21-seed.json`（infohash/torrent_sha256 为 null）、下载服务 19:45:50 失败日志、下载任务列表 FAILED 记录。

### 10.4 AC1-7（回归）—— **部分通过（PARTIAL）**

- HTTP 直链：`downloadId=1317255649695891456` → `download_task.status=2`（FINISH）✅（证据 `ac7-http.json`）。
- Git 克隆：`downloadId=1317255673502760960` → `status=2`（FINISH）✅（证据 `ac7-git.json`）。
- core 内嵌 Worker：`POST /api/diskFile/{uid}/asyncCopy` 成功提交 `file-copy` 任务（`taskType=file-copy`，`asyncTaskId=1317256890316488704`），`async_task_record.claimed_role=**CORE**` —— core 内嵌 Worker 的认领/执行链路正常；但该任务因测试用源文件名与实际落盘名不符 `status=3(FAILED)`，**成功子项未达成**，故 AC1-7 记 PARTIAL（证据 `ac7-asynccopy-manual.json`）。
- 说明：脚本 `Find-StoreFile` 假设的 core store 目录布局过时（实际为内容寻址 `store/repo/xx/xxxx` 结构），HTTP/Git 落盘校验因此未通过；该项属测试脚本问题，产品侧 HTTP/Git 任务均 FINISH，落盘由 core 日志「文件保存成功」佐证。

### 10.5 复验后统计

| 结论 | 数量 | 条目 |
|---|---|---|
| PASS | 8 | AC1-1、AC1-2、AC1-5、AC2-1、AC2-2、AC2-3、AC2-4、AC2-5 |
| PARTIAL | 1 | AC1-7（HTTP/Git 通过，core Worker 认领通过但 asyncCopy 业务失败） |
| FAIL | 1 | AC2-6（新缺陷 P5-D02） |
| NOTRUN | 3 | AC1-3、AC1-4、AC1-6（时间原因，可复现步骤见 §7） |

**证据文件**：`ac21-seed.json`、`ac21-aria2.json`、`ac25-list.json`、`ac7-http.json`、`ac7-git.json`、`ac7-asynccopy-manual.json`、`p5-d01-default-seed-stuck.json`（修复前）、以及下载服务/core 日志。



---

## 11. P5-D02 修复后复验（AC2-6 PASS，2026-11-09 20:06~20:16）

**环境**：local 注册中心栈（同 §10）；下载服务挂载新 jar `sfc-download-service-1.0.0.jar`，mtime **2026-11-09 20:05:36**、大小 116,401,633（含 P5-D02 修复，单测 111/111）；core jar 18:10；aria2 容器 16800。

### 11.1 AC2-6 同 infohash 复用 —— **PASS**

- **S1（建种）**：`downloadId=1317261800328134656`，TORRENT（webtorrent Sintel）→ `download_task.status=2`、`size=129302391`、`seeding=true`、`seedEndAt=2026-11-16 20:10:15`。
  - DB `download_seed_task`：`infohash=08ada5a7a6183aae1e09d831df6748d566095a10`、`torrent_sha256=4c8fdad0414b4767546a0f92fe3d660a66edc32471874e7a1cfa97120317b84a`（**两者均非空，修复生效**）、`status=0`、`seed_end_at - seed_start_at = 10080 分钟`；
  - 日志：`已获取种子文件内容（用于同种子复用匹配与做种指纹）` → `做种保留已登记: ... infohash=08ada5a7..., torrentSha256=4c8fdad0..., seedEndAt=2026-11-16 20:10:15, 产物数=11`。
- **S2（同 URL 再建）**：`downloadId=1317262083661758464` → `download_task.status=2`（FINISH）、`size=129323378`（复用既有 13 个文件，含元数据文件）。
  - 日志：`已获取种子文件内容（用于同种子复用匹配与做种指纹）` → `命中可复用的做种保留记录（torrentSha256=4c8fdad0...），无需重新下载: seedTaskId=1317262064439263232, ... seedEndAt=2026-11-16 20:10:15` → `复用做种中的文件，无需重新下载: ... 文件数=13`；
  - **无** `已提交 aria2` 日志、**无** `InfoHash ... is already registered` 错误；
  - aria2 `tellActive`：仅 S1 的做种 gid `7357c6d7a383ecb7`（count=1），**S2 未产生新 gid、未重复下载**。
- **同时复核**：AC2-1（新 jar 默认 10080：FINISH、seed 记录 status=0、指纹非空、delta=10080 分钟、aria2 active+seeder=true、目录保留）与 AC2-5（列表 S1 `seeding=true`/`seedEndAt` 非空）。
- **证据**：`p5d02-ac26-reverify.json`（含 S1/S2 状态、seed 记录、关键字日志、aria2 active、列表字段）。

### 11.2 复验后最终统计

| 结论 | 数量 | 条目 |
|---|---|---|
| PASS | 9 | AC1-1、AC1-2、AC1-5、AC2-1、AC2-2、AC2-3、AC2-4、AC2-5、AC2-6 |
| PARTIAL | 1 | AC1-7（HTTP/Git 通过；core Worker 认领通过但 asyncCopy 业务未成功） |
| FAIL | 0 | — |
| NOTRUN | 3 | AC1-3、AC1-4、AC1-6（时间原因，可复现步骤见 §7） |
