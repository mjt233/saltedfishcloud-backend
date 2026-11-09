# aria2 停机续传 + 做种保留：P5 验收清单（正式 AC 版）

> **用途**：P5 测试与验收（task-5）逐条执行/判定 AC1 / AC2 的清单。
> **AC 来源**：Lead 于 2026-11-09 下发的**正式 AC 表**（本清单以该表为准；设计契约见 `docs/plan/aria2-resume-seed-design.md`）。
> **联调脚本**：`scripts/aria2-resume-seed-e2e.ps1`（阶段与 AC 一一对应，标注【脚本】的步骤已自动化）。
> **状态**：AC 编号与判定标准已按正式表更新；P1~P4 完成后执行，执行结果汇总进
> `microservices/sfc-download-service/docs/resume-seed-verification-report.md`。

---

## 1. 判定口径与证据要求

| 结论 | 判定条件 |
|---|---|
| **通过（PASS）** | 在真实运行环境（core + sfc-download-service + 真实 aria2 进程；跨实例为 MySQL + 两实例）执行步骤，全部判定标准满足且证据完整。 |
| **失败（FAIL）** | 存在判定标准不满足；必须附**现象 / 复现步骤 / 预期 / 实际 / 日志**，并区分「实现缺陷 / 环境问题 / 测试用例问题」，通过 Lead 转达开发角色（QA 不直接改业务代码）。 |
| **未执行（NOTRUN）** | 因依赖未就绪、环境限制（网络/端口/时间）而**未在真实环境执行**；必须写原因 + 可直接复现的命令序列 + 预期观察点。 |
| **不适用（N/A）** | 场景在当前架构下明确不成立（如 SQLite 模式下的跨实例接管），需引用设计文档依据。 |

**每条 AC 的证据必须包含**（报告逐条引用证据文件名）：

1. **命令**：实际执行的命令（HTTP / SQL / docker / RPC）；
2. **日志**：关键日志关键字（core 或下载服务，带任务 id 与时间戳）；
3. **DB**：core 库 `async_task_record` / `async_task_checkpoint`，下载服务库 `download_task` / `download_seed_task` 的行级快照（操作前后）；
4. **aria2 RPC**：`tellStatus/tellActive` 的原始返回（gid、status、completedLength、seeder、bittorrent）；
5. **文件系统**：任务目录「存在/删除」清单与大小。

**加速手法（Lead 已允许，报告中注明）**：做种到期不真等 7 天——AC2-1 只核对 `seed_end_at - seed_start_at ≈ 10080 分钟`；AC2-2/AC2-3 使用 `download.aria2.seed-time-minutes` 的**小值**（如 2 分钟）触发真实到期；不采用直接改库时间（以配置为优先，保持链路真实）。

**统一前置**（每条 P2P 用例执行前确认）：

- [ ] 核心配置 `download.aria2.enabled=true`，`GET /api/task/download/capabilities?refresh=true` 中 MAGNET/TORRENT `available=true`
- [ ] aria2 RPC 可达（`aria2.getVersion` 成功）、`download.aria2.stall-timeout=0`（测试期关闭无进度超时，失败用例单独临时改小）
- [ ] 记录测试前后 `download_task` / `download_seed_task` / `async_task_record` / `async_task_checkpoint` 行数与最大 id（残留核对）
- [ ] 测试机 6800 端口被原生 aria2c 占用属预期；QA 使用 16800（容器）

---

## 2. 环境与联调方式（准备阶段实测）

| 项 | 结论 |
|---|---|
| JDK / Maven | JDK 25.0.2 + Maven 3.9.15 可用 |
| Docker | Docker Desktop 4.36.0 / Engine 27.3.1（linux/amd64）daemon 可用 |
| aria2 | 镜像 `p3terx/aria2-pro` 本地已有、registry 可拉取；自定义 conf + 端口 16800 起容器后宿主 JSON-RPC 实测通过（1.36.0）；**本机另有原生 aria2c 1.37.0 占用 6800**，联调不触碰 |
| 联调方式（方案 A） | `docker-compose-microservice-full.yml` + `scripts/qa-p5-compose.override.yml`：主机构建 jar 只读卷覆盖镜像内 jar（不重建镜像），端口 18087/18092/16800；数据在独立 `%TEMP%\sfc-p5\...`，不污染仓库 `data/` |
| 优雅停机 | 仅容器内 `docker stop` 可可靠触发 JVM shutdown hook → detach；宿主机 Windows 进程只能强杀（**不得**用宿主机进程验证 AC1-1） |
| 跨实例（AC1-6） | 追加 `scripts/qa-p5-compose.mysql.override.yml`：下载服务切 MySQL（`sfc_download` 库）+ 第二实例 `sfc-download-service-2`（18093），共享 MySQL / 共享下载卷 / 同 Nacos |
| 隔离边界 | 不动现有 `nacos` 容器、6800 原生 aria2、`data/core/mysql` 旧数据、用户 `.env` 以外的配置；结束 Teardown 只清理本轮创建的容器 |

**统一服务/端口**：core `http://127.0.0.1:18087`（admin Cookie）、下载服务 `http://127.0.0.1:18092`（`X-Sfc-Uid` 信任头）、aria2 RPC `http://127.0.0.1:16800/jsonrpc`、第二实例 `18093`。

**常用接口**：

| 用途 | 接口 |
|---|---|
| 创建/列表/取消下载任务 | `POST/GET/DELETE /api/task/download`（下载服务，18092） |
| 能力声明（含热刷新配置） | `GET /api/task/download/capabilities?refresh=true` |
| 管理员登录（Cookie） | `POST /api/user/token`（form：user/password） |
| core 统一配置写入 | `PUT /api/admin/sys/config/batchSetConfig`（admin Cookie） |
| 异步任务记录/日志/进度 | `GET /api/asyncTask/getById`、`getLog`、`POST getProg`、`GET listWorker` |
| 触发 core 内嵌 Worker 任务 | `POST /api/file/{uid}/asyncCopy`（file-copy 任务由 CORE 角色执行） |

**日志关键字（P1~P4 实现已核对，执行时以最终代码为准）**：

| 场景 | 关键字（子串匹配） |
|---|---|
| 优雅停机挂起 | `已请求任务[` + `挂起`（`已请求任务[x]挂起（等待执行线程保存检查点后退出）`） |
| 挂起重新排队 | `已挂起并重新排队（不消耗重试次数）` |
| 失联按检查点回收 | `执行者失联，已按检查点重新排队（不消耗重试次数）` |
| 下载服务接管既有 aria2 | `已接管既有 aria2 任务: gid=`（或 `已接管 aria2 任务: gid=`） |
| P2P 协调器接管 | `接管 P2P 任务: downloadId=..., phase=..., resumeCount=...` |
| 做种复用命中 | `命中可复用的做种保留记录` / `复用做种中的文件，无需重新下载` |
| 做种到期清理 | `发现 N 条到期的做种记录，开始抢占清理` / `做种记录已清理: id=...` |
| aria2 不可达降级 | `aria2 不可达，跳过本轮做种清理（不置终态，下轮重试）` |

---

## 3. AC1：任务框架 + 下载接管（正式 AC 表）

### AC1-1 优雅停机不杀 aria2（【脚本】Phase Resume 前半段）

| 项 | 内容 |
|---|---|
| **操作** | 创建 MAGNET/TORRENT 任务 → 等下载中（`download_task.status=1`、aria2 有活动 gid、进度 >0 且未完成）→ `docker stop -t 45 sfc-download-service`（SIGTERM 优雅停机）→ 停机后观察 |
| **判定标准** | ① 任务目录保留（未删除）；② aria2 侧该 gid 仍为 `active`/`waiting`（**不是** removed/error）；③ `async_task_record.status` 回到 `0`（等待中，或由失联回收回 0）；④ 下载服务日志出现「已请求任务[x]挂起」与 core 日志出现「已挂起并重新排队（不消耗重试次数）」；⑤ `async_task_checkpoint` 行仍存在；⑥ 停机期间无 FINISH 上报 |
| **证据** | 停机前后 DB 快照（status/retry_count/claimed_*/checkpoint）、aria2 `tellActive`/`tellStatus` 原始 JSON、下载服务与 core 日志片段、任务目录清单 |
| **观察点** | `retry_count` **不得增加**；`claimed_worker` 清空；`detach-await-seconds`（默认 10s）内退出时序 |

### AC1-2 重启接管不重复下载（【脚本】Phase Resume 后半段）

| 项 | 内容 |
|---|---|
| **操作** | 接 AC1-1，尽快重启下载服务（尽量在 aria2 下载完成前）→ 等待任务重新认领 → 跟踪进度至 FINISH |
| **判定标准** | ① 任务被重新认领；② 日志出现「已接管既有 aria2 任务：gid=...」，且**无新的 addUri/addTorrent 提交**（无重新提交日志、gid 不变）；③ 进度继续增长（重启后 `loaded` > 停机时 `loaded`）；④ 最终 upload + FINISH，`download_task.status=2`；⑤ 临时/工作目录只有原目录（未新建）；⑥ 检查点 `resumeCount` 递增 1，`localDir/aria2Dir` 不变 |
| **证据** | 接管前后检查点 JSON diff、aria2 gid 与进度快照、下载服务接管日志、`download_task` 状态轨迹、目录清单 |
| **备注** | 若资源在停机期间即下载完成（较快资源），本条转由 AC1-4 覆盖，并在报告中说明（不得把未观察到的「进度继续」写成通过） |

### AC1-3 崩溃恢复（【脚本】Phase CrashRecovery）

| 项 | 内容 |
|---|---|
| **操作** | 创建 P2P 任务 → 等下载中 → `docker kill`（SIGKILL，模拟 kill -9，无 shutdown hook）→ 等 core 健康检查回收（`TaskHealthChecker` 30s 周期）→ 重启下载服务 → 等接管完成 |
| **判定标准** | ① core 将任务回收为等待中（日志「执行者失联，已按检查点重新排队（不消耗重试次数）」）；② `retry_count` **不增加**；③ 重启后按检查点接管（日志「已接管既有 aria2 任务：gid=...」）→ FINISH；④ 检查点未被误删 |
| **证据** | kill 前后 DB 快照、core 回收日志、重启接管日志、最终状态 |
| **观察点** | 回收延迟 ≤ 健康检查周期 + 认领周期；若 core 也重启，另看 `StartupTaskRecovery` 启动回收分支 |

### AC1-4 离线期间下载完成（【脚本】Phase OfflineComplete）

| 项 | 内容 |
|---|---|
| **操作** | 创建 P2P 任务 → 下载中停服务 → **等 aria2 完成**（poll `tellStatus` 到 complete）→ 重启服务 → 等接管完成 |
| **判定标准** | ① 重启后接管识别已完成 gid，直接采集产物并上传 → FINISH；② 日志**无重新提交**（无 addUri/addTorrent）；③ 网盘产物完整（大小 = aria2 totalLength）；④ `download_task.status=2`；⑤ 检查点 FINISH 后删除 |
| **证据** | 停机期间 aria2 完成快照（completedLength/totalLength）、重启后日志、网盘文件清单与大小、DB 状态 |
| **观察点** | 检查点阶段轨迹 SUBMITTED/DOWNLOADING → DOWNLOADED → UPLOADING → UPLOADED |

### AC1-5 用户取消正常清理（【脚本】Phase Cancel）

| 项 | 内容 |
|---|---|
| **操作** | 创建 P2P 任务 → 下载中调用 `DELETE /api/task/download?taskId=...` |
| **判定标准** | ① `download_task.status=4`（CANCEL）；② aria2 gid 被 `forceRemove`/已不存在（`tellStatus` removed 或查不到、`tellActive` 无该 gid）；③ 本地任务目录删除；④ 检查点删除；⑤ 网盘无该任务产物 |
| **证据** | 取消前后 DB、aria2 RPC 快照、目录删除前后清单、取消接口响应 |

### AC1-6 跨实例接管（**必须优先尝试**；【脚本】Phase CrossInstance）

| 项 | 内容 |
|---|---|
| **前置** | MySQL 数据源（P4）+ 两个下载服务实例共享 MySQL/共享下载卷/同一注册中心。使用 `scripts/qa-p5-compose.mysql.override.yml`：实例 1（18092）+ 实例 2（18093），同 `serviceId=sfc-download-service`。**SQLite 场景不适用**（架构限制，文档已写明，记 N/A）。 |
| **操作** | 两实例就绪（`GET /api/asyncTask/listWorker` 可见 sfc-download-service 两个 workerId）→ 创建 P2P 任务 → 确认被实例 A 认领（`async_task_record.claimed_worker`）→ `docker stop` A（优雅停机）→ 确认实例 B 认领并接管同一 gid → 完成 |
| **判定标准** | ① 两实例均注册可见；② 任务被 A 认领后停 A，任务回等待中（不耗重试）；③ B 认领，日志「已接管既有 aria2 任务：gid=...」且 gid 与停机前一致（未重新提交）；④ B 完成 upload + FINISH；⑤ `download_task.status=2`、大小正确 |
| **证据** | `listWorker` 两实例输出、claimed_worker 前后值、两实例日志、aria2 gid 快照、DB 状态 |
| **降级** | 环境/端口/时间不允许时记 **NOTRUN**，给出可复现命令（compose 文件 + 手动步骤）；SQLite 下记 **N/A（架构限制）** |

### AC1-7 回归（【脚本】Phase Regression + Phase SeedZero）

| 子项 | 判定标准 | 证据 |
|---|---|---|
| HTTP 直链下载 | 创建 HTTP 任务 → FINISH；网盘文件大小/SHA256 与源一致；无临时目录残留 | 任务状态、文件清单/摘要、日志 |
| Git 克隆 | 创建 GIT 任务（公开仓库）→ FINISH；产物正确；无残留 | 同上（网络不可达记 NOTRUN） |
| core 内嵌 Worker 任务 | `POST /api/file/{uid}/asyncCopy` → `async_task_record.claimed_role=CORE`、status=FINISH | 任务记录、core 日志 |
| 做种配置=0 时完成即清理 | 见 AC2-4（同一证据可同时标记 AC1-7 该子项与 AC2-4） | 同 AC2-4 |

---

## 4. AC2：做种保留生命周期（正式 AC 表）

### AC2-1 下载完成保留做种（【脚本】Phase Seed 第 1 段）

| 项 | 内容 |
|---|---|
| **操作** | 确认 `download.aria2.seed-time-minutes=10080`（默认值）→ 创建 P2P 任务 → 等 FINISH |
| **判定标准** | ① 本地任务目录保留；② aria2 该 gid 仍 `active` 且 `seeder=true`（或 `tellStatus` 的 `bittorrent` 信息符合、`uploadSpeed` 可观测）；③ `download_task.status=2`（FINISH）；④ `download_seed_task` 新增记录：`status=0`、`download_id` 对应、`content_gid/infohash(或 torrent_sha256)/aria2_dir/local_dir/seed_start_at/seed_end_at` 有值；⑤ `seed_end_at - seed_start_at ≈ 10080 分钟`（允许秒级误差） |
| **证据** | seed 记录行、aria2 `tellStatus`、目录清单、任务状态、配置值快照 |

### AC2-2 到期清理（【脚本】Phase SeedExpire）

| 项 | 内容 |
|---|---|
| **操作** | 配置 `seed-time-minutes` 为小值（如 2 分钟）→ 创建 P2P 任务 → FINISH 并登记做种 → 等待 `seed_end_at` 到期 + 一个 `SeedTaskKeeper` 周期（测试环境 `sfc.download.seed.check-interval-millis=10000`）→ 再等一轮验证幂等 |
| **判定标准** | ① aria2 `removeDownloadResult` 成功（该 gid 不再 active/查不到）；② 本地目录删除；③ 记录 `status=2`、`finish_at` 非空、`message` 为清理说明；④ 列表 `seeding=false`；⑤ **重复触发无副作用**：再等一轮无异常、不重复删除、记录不变 |
| **证据** | 到期前后 seed 记录、aria2 前后快照、目录删除前后清单、清理日志（`发现 N 条到期的做种记录`/`做种记录已清理`）、第二轮日志 |

### AC2-3 做种期间重启（【脚本】Phase SeedExpire 中段）

| 项 | 内容 |
|---|---|
| **操作** | 做种记录生效中（`status=0`）→ 重启下载服务 → 核对 seed_end_at → 等到期后仍能清理 |
| **判定标准** | ① 重启后 `seed_end_at` 与重启前一致（按落库时间，不由重启重新计算）；② 到期后仍被 `SeedTaskKeeper` 正常清理（status=2、aria2 移除、目录删除） |
| **证据** | 重启前后 seed 记录对比、清理日志与终态 |

### AC2-4 配置=0 旧行为（【脚本】Phase SeedZero；同时作为 AC1-7 子项）

| 项 | 内容 |
|---|---|
| **操作** | 配置 `seed-time-minutes=0` → 创建 P2P 任务 → 等 FINISH |
| **判定标准** | ① 不产生 `download_seed_task` 记录（或按实现不保留，语义相同）；② aria2 执行 `removeDownloadResult`（无残留结果/活动）；③ 本地目录删除；④ `download_task.status=2` |
| **证据** | seed 记录查询（空）、aria2 快照、目录清单、任务状态 |

### AC2-5 列表字段（【脚本】Phase Seed 第 2 段）

| 项 | 内容 |
|---|---|
| **操作** | 做种中任务与普通任务（HTTP/GIT/历史数据）分别查任务列表 `GET /api/task/download?uid=<uid>&page=1&size=...` |
| **判定标准** | ① 做种中任务返回 `seeding=true`、`seedEndAt` 非空（与 seed 记录 seed_end_at 一致）；② 非 P2P 任务、无做种记录任务返回 `seeding=false`（或 null）且 `seedEndAt=null`；③ 字段为 transient，不入库 |
| **证据** | 列表接口 JSON 片段（对应任务项）、DB 表结构确认（无新增列） |

### AC2-6 同 infohash 复用（【脚本】Phase Seed 第 3 段）

| 项 | 内容 |
|---|---|
| **操作** | 做种保留期内，对**同一磁力链/种子**再次创建下载任务 |
| **判定标准** | ① 命中做种记录，**跳过 aria2 提交**（无新 gid，日志「命中可复用的做种保留记录」/「复用做种中的文件，无需重新下载」）；② 直接读取既有目录产物上传并 FINISH；③ 不出现 `InfoHash is already registered` 失败；④ 原做种记录 `seed_end_at` 不变（复用不延长） |
| **证据** | 复用任务日志、aria2 gid 前后对比、seed 记录 before/after、任务状态与网盘产物 |

---

## 5. 单测复跑命令（P5 第 1 项）

> 根 pom 默认 `skipTests=true`，必须显式 `-DskipTests=false`。

```powershell
mvn -q -pl sfc-api,sfc-core-task -DskipTests=false test
mvn -q -f microservices/pom.xml -pl sfc-task-worker,sfc-download-service -DskipTests=false test
```

记录各模块 Tests run/Failures/Errors/Skipped 与 Surefire 报告路径；按设计 §7 审查覆盖点（Execution 挂起/检查点、Runner、ReportService detached、CheckpointService；P2pTaskState 往返、Coordinator resume 三分支、上传幂等、收口矩阵、OrphanReconciler；SeedTaskKeeper、Registrar、infohash/sha256、列表填充）。

---

## 6. 执行顺序（【脚本】`-Phase All`）

`Preflight → Build → StackUp → Configure → Smoke → Resume（AC1-1/1-2）→ OfflineComplete（AC1-4）→ CrashRecovery（AC1-3）→ Cancel（AC1-5）→ Regression（AC1-7 部分）→ Seed（AC2-1/2-5/2-6）→ SeedExpire（AC2-2/2-3）→ SeedZero（AC2-4 + AC1-7 零配置子项）→ CrossInstance（AC1-6，切 MySQL+双实例）→ Teardown`

---

## 7. 结束清理与环境恢复

- [ ] 停止并清理本轮创建的容器（`sfc-core`、`sfc-download-service`、`sfc-download-service-2`、`sfc-aria2`、`sfc-p5-mysql`、`sfc-p5-redis`），**保留 nacos 与既有镜像**
- [ ] aria2 `purgeDownloadResult`；确认 `tellActive` 为空（做种类用例的测试数据按需清理）
- [ ] core 统一配置恢复测试前值（`download.aria2.*`，记录改前值）
- [ ] 网盘测试目录与本地工作目录清理；记录测试任务 id 清单，确认无运行中残留
- [ ] 不执行任何 git 写命令；测试产物不提交

---

## 8. 缺陷记录模板

```
缺陷 ID：P5-D<nn>
发现于：AC<编号>（时间 / 证据目录）
现象：（可复现步骤 + 预期 vs 实际）
证据：（DB 快照 / 日志片段 / RPC 返回 / 文件清单）
初步分类：实现缺陷 / 环境问题 / 测试用例问题
影响 AC：…
回归建议：…
（由 Lead 转达对应开发角色；QA 不直接改业务代码）
```
