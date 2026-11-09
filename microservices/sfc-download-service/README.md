# sfc-download-service —— 离线下载微服务

> 原宿主插件 `sfc-ext/sfc-ext-download` 的微服务化版本（参考先例：`microservices/sfc-ai-service` 由 `sfc-ext-ai` 迁入）。
> 插件形态已从仓库删除：不再被 `sfc-core` 以 jar 插件加载，也没有 `plugin-lib`。

## 一、定位

| 维度 | 插件期（已删除） | 微服务期（本服务） |
|---|---|---|
| 运行形态 | 与 core 同 JVM、同数据源 | 独立进程（默认端口 **8092**）；默认独立 SQLite `download.db`，可经环境变量切换 MySQL（见 §4.4） |
| 任务执行 | 宿主异步任务框架 + `DownloadAsyncTaskFactory` | `sfc-task-worker` SDK，以 `SERVICE` 角色认领 `offline-download` 任务 |
| 下载能力 | 仅 HTTP/HTTPS | HTTP / GIT（HTTP(S) + SSH 克隆）/ MAGNET / TORRENT（+ ED2K 占位），按 `DownloadEngine` 可扩展 |
| 网盘读写 | 直接调用宿主 `DiskFileSystem` | 经 `sfc-remote-client` 回调 core 的 `/api/internal/file/v2/**` |
| 下载代理 | 直接读宿主 `proxy` 表 | 经 core 内部接口 `/api/internal/proxy/available` 解析（创建时快照）；HTTP(S) / SSH 的 Git 克隆同样生效 |
| 业务配置 | 宿主配置项 | Git 参数与 aria2 运行参数（开关 / 进度轮询间隔 / 无进度超时 / 种子体积上限 / **做种保留时长**）由 **sfc-core 统一配置**（后台「插件配置」面板）管理，本服务经 `SfcCoreConfigFeignClient` 读取，改完最长 5 秒生效；aria2 的 RPC 地址、令牌与共享目录仍为**部署期配置**（本服务 `application.yml` / 环境变量），修改后需重启 |
| 前端资源 | core 从插件 jar 的 `static/` 读取 | core 按注册中心 metadata `sfc.plugin.autoload` 从本服务 `/static/**` 拉取合并 |
| 数据 | 宿主 `xyy.db` 的 `download_task` | 本服务数据库（默认 SQLite `download.db`，可切 MySQL）：`download_task` / `download_seed_task` / `ssh_credential` / `ssh_host_key` 等；**历史数据不迁移，SQLite → MySQL 切库同样不迁移** |

## 二、架构与数据流

```
浏览器（主 SPA，前端 URL 由前端侧手动调整）
  │  /api/service/sfc-download-service/task/download/**     ← 网关默认动态路由（无需显式配置）
  ▼
sfc-gateway ──(discovery locator: 剥离 /api/service/{serviceId} 并补 /api)──► sfc-download-service:8092
                                                                              ├─ DownloadController        /api/task/download/**
                                                                              ├─ SshCredentialController   /api/task/download/ssh-credential（用户凭据）
                                                                              ├─ SshHostKeyController      /api/task/download/ssh-host-key（管理员）
                                                                              ├─ MetaController            /api/meta/pluginInfo
                                                                              ├─ DownloadTaskHandler       SfcTaskHandler(offline-download)
                                                                              │    ├─ P2pTaskCoordinator      磁力/种子：检查点、接管续传、做种登记、收口矩阵
                                                                              │    └─ DownloadEngineRegistry（按 type 路由）
                                                                              │         ├─ HttpDownloadEngine     REST 流式（代理/忽略证书/超时）
                                                                              │         ├─ GitDownloadEngine      JGit 克隆（HTTP(S) / SSH）→ 目录树
                                                                              │         ├─ Aria2DownloadEngine    磁力/种子 aria2 JSON-RPC 原子能力（提交/轮询/采集/丢弃）
                                                                              │         └─ Ed2kDownloadEngine     占位，恒不可用
                                                                              ├─ Aria2OrphanReconciler      aria2 孤儿任务对账（启动一次 + 周期）
                                                                              ├─ SeedTaskKeeper            做种保留到期清理（抢占 + 停做种 + 删目录）
                                                                              ├─ DownloadSaveService       单文件 / 目录树递归落盘
                                                                              ├─ SfcDownloadConfigService  统一配置读取（Git + aria2 运行参数，TTL 5s）
                                                                              ├─ SshCredentialService      用户 SSH 凭据（明文存 ssh_credential，接口不回显）
                                                                              ├─ SshHostKeyService         主机密钥 TOFU 信任记录（ssh_host_key，全局共享）
                                                                              └─ download.db（download_task + download_seed_task + ssh_credential + ssh_host_key；默认 SQLite，可切 MySQL）
                                                                                      ▲
                                    /api/internal/task/{submit,query,cancel,checkpoint/save} ─────────┤（sfc-task-worker SDK）
                                    /api/internal/proxy/available         ───────────┤（sfc-remote-client SDK）
                                    /api/internal/config/batchGet         ───────────┤（Git 与 aria2 运行参数统一配置）
                                    /api/internal/file/v2/{list,mkdirs,upload} ──────┘（落盘）
                                                                                      │
                                                                                      ▼
                                                                                 sfc-core:8087
```

- **类型路由**：异步任务类型始终是 `offline-download`，`DownloadTaskHandler` 按参数里的 `type` 交给对应引擎。
  新增下载类型不需要新增任务类型，也不需要改动 Handler / Service / Controller。
- **任务状态双源**：本地 `download_task.status` 只是镜像（用于分页筛选），列表查询时对非终态任务调用
  `SfcTaskWorkerClient.queryTasks` 用 core 的真实状态/进度校准（core 不可用时 fail-soft 回退镜像）。
  P2P 任务停机挂起后 core 会把任务重置回「等待中」：接管成功后继续执行、任务 id 不变；
  挂起 / 失联回收后由检查点接管的 P2P 任务**不消耗** core 的故障转移重试次数（`retry_count`）。
- **停机续传与接管（MAGNET / TORRENT）**：P2P 任务把 aria2 现场（gid、任务目录、阶段、上传进度等）以
  检查点 JSON 保存到 core 的 `async_task_checkpoint` 表。执行者优雅停机时先请求挂起、保存检查点后退出，
  任务回到队列（不消耗重试次数）；重启或其它实例认领后按检查点重连原 gid 继续下载，任务日志出现
  「已接管既有 aria2 任务: gid=...」。aria2 是独立守护进程，**本服务停机不影响其继续下载**；
  gid 丢失时只能在同一目录以 `continue=true` 尽力续传（不保证）。
- **生命周期收口矩阵**：P2P 任务的 aria2 结果与本地目录不再无条件删除，统一按结局处理：做种保留 /
  未做种完成清理 / 上传失败 / 取消 / 失败 / 挂起（挂起时 aria2 任务与本地目录都保持不动）。
- **做种保留**：下载完成后按统一配置 `download.aria2.seed-time-minutes`（默认 `10080` 分钟 = 7 天；
  `0` = 不做种）登记 `download_seed_task`，保留本地目录与 aria2 任务继续做种；到期由 `SeedTaskKeeper`
  停止做种并删除本地目录（网盘副本保留）。同 infohash / 同种子在保留期内再次提交时直接复用文件、不重复下载。
- **aria2 孤儿任务对账**：`Aria2OrphanReconciler` 在应用启动后执行一次并按
  `sfc.download.aria2.reconcile-interval-millis`（默认 5 分钟，`<=0` 关闭）周期对账，只清理 `dir` 位于
  本服务任务工作区、对应下载任务已终态且未被做种保护的任务；工作区之外的 aria2 任务**绝不触碰**，
  查不到下载任务记录时只 WARN 不删除。
- **上传幂等**：P2P 上传前写检查点 `UPLOADING`、上传成功后写 `UPLOADED`；接管实例看到 `UPLOADED`
  直接补报任务完成、不重复上传。上传阶段收到挂起请求时允许本次上传完成后再退出（见 §九已知边界）。
- **中断**：`DELETE /api/task/download?taskId=` → `SfcTaskWorkerClient.cancel` → core `AsyncTaskManager.interrupt`
  → core 按服务发现把中断指令下发到实际执行实例 → Handler 触发 `DownloadContext#cancel()`（关闭下载流 /
  `aria2.forceRemove` / 结束 Git 克隆）。
- **Git 代理与 SSH 克隆**：`GIT` 类型同样消费创建时快照的代理——HTTP(S) 克隆走 `TransportHttp` 注入的
  `HttpConnectionFactory`，SSH 克隆走 `SshdSessionFactory` 的 `ProxyDataFactory`（HTTP CONNECT / SOCKS，即 SOCKS5），
  均按任务注入、不改 JVM 全局代理；`git://` 地址不支持代理（创建任务时带代理返回 400）。
  SSH 认证方式为私钥 / 带口令私钥 / 密码，**不读取宿主机 `~/.ssh`**。
- **SSH 凭据与主机信任记录**：SSH 克隆按任务参数 `sshCredentialId` 从 `ssh_credential` 读取当前用户的凭据，
  任务参数只保存凭据 id（私钥/口令/密码不随任务下发、接口不回显）；首次连接未知主机时把主机密钥写入
  `ssh_host_key`（TOFU，**全局共享**），指纹变化直接失败，管理员在信任记录中删除旧记录后重试即可；
  首次信任记录**写库失败时拒绝本次连接**（fail-closed）。私钥认证要求 SSH 地址携带用户名，或凭据中配置 `username`
  （二者都没有时创建任务返回 400）。
- **落盘**：下载完成后统一走 `DownloadSaveService`。单文件直接上传并按 `.1`/`.2` 规避重名；多文件或目录树
  （BT 多文件、Git 仓库）在 `savePath` 下建顶层目录（同样规避重名），再递归 `mkdirs` + 逐文件上传。
  ⚠️ **可保存的单文件大小受 core 的 `spring.servlet.multipart.max-file-size` 约束**（默认 32GiB），
  且 multipart 会先在 core 侧落临时文件再写入存储，上传期间 core 需约 2 倍文件大小的空闲磁盘；
  超过该上限时任务会以 `413 Content Too Large` 失败（需调大 core 该配置）。
- **配置来源（统一配置 + 部署配置）**：Git 运行参数与 aria2 运行参数（P2P 开关、进度轮询间隔、无进度超时、
  种子体积上限、做种保留时长，`download.aria2.*`）由 core 统一配置面板管理，本服务按 5 秒 TTL 缓存读取，
  改完无需重启；core 不可用时回落为配置实体上的注解默认值（只 WARN 一次），不影响 HTTP / Git 下载。
  aria2 的 RPC 地址、令牌与共享目录仍属于**部署期配置**（`sfc.download.aria2.*` / `SFC_DOWNLOAD_ARIA2_*`），
  修改后需重启本服务。
- **前端资源**：core 的 `GET /api/plugin/autoLoad.js` 会把本服务 `/static/index.umd.js`、`/static/style.css`
  合并返回（与插件期行为一致，前端加载链路不变）。

## 三、依赖

| 依赖 | 用途 |
|---|---|
| `com.sfc.cloud:sfc-common` / `sfc-common-data` | 响应信封/异常/工具/分页/配置注解 + JPA 基座（`SfcAuditModel`/`SfcBaseRepo`/`@SfcUID`） |
| `com.sfc.cloud:sfc-remote-client` | 回调 core 的网盘文件、代理查询、系统配置接口 |
| `com.sfc.cloud:sfc-task-worker` | 异步任务执行者 SDK（认领/执行/进度/日志/中断/停机） |
| `com.sfc.cloud:sfc-local-registry-client` | 未启用 Nacos 时接入 core 的本地注册中心 |
| `spring-cloud-starter-alibaba-nacos-discovery` | 启用 Nacos 时注册/发现 |
| `spring-boot-starter-data-jpa` + `sqlite-jdbc` + `hibernate-community-dialects` | 独立数据源（默认 SQLite；SQLite 方言来自 community-dialects） |
| `com.mysql:mysql-connector-j`（runtime） | 可选：把数据源切换为 MySQL（多实例共享下载库，见 §4.4）；版本由 Spring Boot BOM 管理 |
| `org.eclipse.jgit:org.eclipse.jgit:7.3.0.202506031305-r` | Git 克隆（纯 Java，部署机**无需**安装 git 命令行） |
| `org.eclipse.jgit:org.eclipse.jgit.ssh.apache:7.3.0.202506031305-r` | Git SSH 传输（Apache MINA sshd 2.15，纯 Java，部署机**无需**安装 git / ssh 客户端；与 JGit 主包严格同版本） |

## 四、配置

### 4.1 `application.yml`（部署期，修改需重启）

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `server.port` | `8092` | 服务端口（demo 8090 / ai 8091） |
| `spring.datasource.driver-class-name` | `org.sqlite.JDBC` | 环境变量 `SFC_DOWNLOAD_DB_DRIVER`；切 MySQL 时置 `com.mysql.cj.jdbc.Driver`（见 §4.4） |
| `spring.datasource.url` | `jdbc:sqlite:./download.db?journal_mode=WAL&busy_timeout=5000` | 环境变量 `SFC_DOWNLOAD_DB_URL`；本服务独立库，`ddl-auto=update` 自动建表，**启动 cwd 决定 SQLite 文件位置**；切 MySQL 见 §4.4 |
| `spring.datasource.username` / `password` | 空 / 空 | 环境变量 `SFC_DOWNLOAD_DB_USERNAME` / `SFC_DOWNLOAD_DB_PASSWORD`；SQLite 无账号体系，默认留空 |
| `spring.datasource.hikari.maximum-pool-size` / `minimum-idle` | `1` / `1` | 环境变量 `SFC_DOWNLOAD_DB_POOL_SIZE` / `SFC_DOWNLOAD_DB_MIN_IDLE`；SQLite 固定单写连接避免 `SQLITE_BUSY_SNAPSHOT`，MySQL 建议调大（如 8 / 2） |
| `sfc.internal.key` | `${SFC_INTERNAL_KEY:}` | 服务间内部鉴权共享密钥，**必须与 core 完全一致**（≥32 字符） |
| `spring.cloud.sfc-local.discovery.*` | 见文件 | local 注册中心客户端（默认启用） |
| `spring.cloud.nacos.discovery.*` | `enabled=false` | 启用 Nacos 时置 true 并关闭 `sfc-local` |
| `metadata.sfc.plugin.autoload` | `/static/index.umd.js,/static/style.css` | 供 core 合并前端自动加载资源（local/nacos 两处都要配） |
| `metadata.sfc.plugin.info` | `/api/meta/pluginInfo` | 供 core 后台拉取本服务的配置面板（local/nacos 两处都要配） |
| `sfc.task.worker.max-concurrent-tasks` | `8` | 并发下载槽位数（IO 密集型，按带宽/磁盘能力调整） |
| `sfc.task.worker.enabled` | `true` | 关闭后不认领任务，也不暴露中断端点 |
| `sfc.download.config.refresh-interval-millis` | `5000` | 统一配置（Git + aria2 运行参数）快照缓存时长（毫秒），即后台改配置后的最长生效延迟 |
| `sfc.download.capability.cache-seconds` | `30` | 下载能力探测（aria2 连通性）结果缓存时长（秒） |
| `sfc.download.aria2.rpc-url` | `http://127.0.0.1:6800/jsonrpc` | 环境变量 `SFC_DOWNLOAD_ARIA2_RPC_URL`；aria2 JSON-RPC 地址。跨容器部署填容器网络地址（如 `http://aria2:6800/jsonrpc`） |
| `sfc.download.aria2.secret` | 空 | 环境变量 `SFC_DOWNLOAD_ARIA2_SECRET`；与 `aria2c --rpc-secret` 一致，只从部署配置读取，日志 / 接口不回显；建议用环境变量 / docker secrets 注入，禁止提交明文 |
| `sfc.download.aria2.shared-dir` | 空（不启用） | 环境变量 `SFC_DOWNLOAD_ARIA2_SHARED_DIR`；下载服务容器内共享根目录（已挂载的绝对路径）。留空 = 与改造前一致（aria2 与下载服务同机 / 同文件系统）；仅 MAGNET / TORRENT 使用 |
| `sfc.download.aria2.remote-dir` | 空（= `shared-dir`） | 环境变量 `SFC_DOWNLOAD_ARIA2_REMOTE_DIR`；aria2 容器内同一共享根目录的 POSIX 绝对路径。留空表示两容器挂载点路径一致 |
| `sfc.download.aria2.reconcile-interval-millis` | `300000` | aria2 孤儿任务对账周期（毫秒）；`<=0` 关闭对账（启动时也不执行）。环境变量 `SFC_DOWNLOAD_ARIA2_RECONCILE_INTERVAL_MILLIS` |
| `sfc.download.resume.upload-stale-millis` | `600000` | P2P 上传阶段陈旧阈值（毫秒，默认 10 分钟）；接管实例发现 `UPLOADING` 检查点在此窗口内时先等待旧实例结果，仍不是 `UPLOADED` 则按陈旧重传 |
| `sfc.download.resume.upload-wait-interval-millis` | `10000` | 等待旧实例上传结果的单次间隔（毫秒） |
| `sfc.download.resume.upload-wait-attempts` | `3` | 等待旧实例上传结果的最大次数 |
| `sfc.download.seed.check-interval-millis` | `60000` | 做种保留到期清理扫描间隔（毫秒，`@Scheduled` 固定延迟） |

> `sfc.download.aria2.rpc-url` / `secret` / `shared-dir` / `remote-dir` 是**部署期配置，修改后需重启本服务**；
> aria2 的运行参数（开关 / 轮询间隔 / 无进度超时 / 种子体积上限 / 做种保留时长）在 core 后台「插件配置」面板维护，
> 改完最长 5 秒生效（见 §4.2）。secret 只从部署配置读取，日志与接口不回显。
> 其余 `sfc.download.*` 内部调优项（对账周期 / 上传幂等窗口 / 做种清理间隔等）同样属于部署期配置，修改后需重启。
> 其中 `sfc.download.aria2.reconcile-interval-millis` / `sfc.download.resume.*` / `sfc.download.seed.check-interval-millis`
> 未在 `application.yml` 中显式声明，默认值来自 `SfcDownloadInternalProperties` 与 `SeedTaskKeeper` 的注解值，
> 可用大写下划线环境变量（如 `SFC_DOWNLOAD_ARIA2_RECONCILE_INTERVAL_MILLIS`）覆盖。
> 共享目录与双路径映射见 §4.3；MySQL 数据源切换见 §4.4；
> `GET /capabilities?refresh=true` 只刷新统一配置快照，不会清除 aria2 连通性探测的 30 秒缓存。

> 本服务**不读取** core 的 `sys.*` 节点，因此无需同步修改 core 侧的配置文件
> （`application.yml` / `application-develop.yml` / `pre-release/config.yml`）。

### 4.2 sfc-core 统一配置（Git + aria2 运行参数，改完无需重启）

Git 与 aria2 的运行期参数不在 yml 中，统一存放在 core 的系统配置表：

- 入口：core 后台「插件配置」→「离线下载」→「Git 克隆」与「aria2 P2P 下载」分组（由注册中心 metadata
  `sfc.plugin.info` 发现，经 `GET /api/meta/pluginInfo` 渲染表单）；
- 生效：本服务按 5 秒 TTL 读取，**最长 5 秒生效**。

配置面板还保留「**SSH 凭据管理**」菜单：声明为 `inputType=template` 的组件
`download-ssh-credential-manager`（前端按现有 template 组件规范实现），用于管理当前登录用户的 SSH 凭据
与（仅管理员）SSH 主机密钥信任记录，**不占用统一配置 key**；组件契约见
[`docs/git-proxy-ssh-clone-frontend-guide.md`](docs/git-proxy-ssh-clone-frontend-guide.md)。

Git 克隆参数：

| 配置 key | 默认值 | 说明 |
|---|---|---|
| `download.git.default-depth` | `0` | 默认克隆深度，`0` = 全量 |
| `download.git.keep-git-dir` | `false` | 是否保留 `.git`，默认删除 |
| `download.git.connect-timeout` | `30` | JGit 连接超时（秒） |

aria2 运行参数：

| 配置 key | 默认值 | 说明 |
|---|---|---|
| `download.aria2.enabled` | `false` | P2P 总开关；关闭时 MAGNET / TORRENT 在创建任务阶段被 400 拒绝 |
| `download.aria2.poll-interval` | `1000` | 进度轮询间隔（毫秒） |
| `download.aria2.stall-timeout` | `300` | 无进度超时（秒）；`0` 表示不限（永久等待，任务会一直占用下载并发位，需手动中断） |
| `download.aria2.torrent-max-size` | `2` | Base64 种子内容上限（MB） |
| `download.aria2.seed-time-minutes` | `10080` | 做种保留时长（分钟）；`0` = 不做种（完成即清理本地文件），上限 `525600`（1 年，超出按上限生效并 WARN）。**仅对提交时读取到该值的新任务生效**，进行中任务沿用提交时的快照 |

> **运行参数与部署期的分界**：以上 5 项在 core 面板维护（改完最长 5 秒生效）；aria2 的
> `rpc-url` / `secret` / `shared-dir` / `remote-dir` 仍是部署期配置（见 §4.1），修改后需重启本服务。
>
> 升级说明：旧的部署配置 `sfc.download.aria2.enabled` / `poll-interval` / `stall-timeout` /
> `torrent-max-size`（及对应 `SFC_DOWNLOAD_ARIA2_*` 环境变量）已**不再读取**；若 core 配置表中仍保留历史
> `download.aria2.*` 旧值，升级后会自动重新生效，否则使用上表默认值（`enabled=false`，需管理员在面板开启）。

**SSH 克隆与 SSH 凭据管理没有统一配置项**：凭据 / 主机信任记录只存放在本服务数据库（默认 SQLite `download.db`，
切 MySQL 后随数据库共享），Git 的克隆行为仍由上述 `download.git.*` 控制。

### 4.3 aria2 部署要求

磁力链与种子依赖外部 aria2 进程（JSON-RPC），支持两种部署方式：

1. **同机部署**（最简单）：aria2 与下载服务运行在同一台机器 / 同一文件系统上，**不配置共享目录**，
   行为与改造前完全一致；`rpc-url` 填本机地址（默认 `http://127.0.0.1:6800/jsonrpc`）。
2. **跨容器 / 跨机部署（共享卷 + 双路径映射）**：两个容器必须挂载**同一份共享存储**，并在两侧分别配置路径：
   - `sfc.download.aria2.shared-dir`：**下载服务容器内**共享根目录（已挂载）的绝对路径，任务目录为
     `<shared-dir>/offline-download/<任务id>-<UUID>`；
   - `sfc.download.aria2.remote-dir`：**aria2 容器内**同一共享根目录的 POSIX 绝对路径；留空表示两容器挂载点
     路径一致（此时等价于 `shared-dir`）；
   - `sfc.download.aria2.rpc-url` 填从下载服务可达的地址，跨容器用容器网络地址（如 `http://aria2:6800/jsonrpc`）。

   映射示例（宿主机 `/host/shared`，两容器路径不同，必须显式配置 `remote-dir`）：

   ```text
   下载服务:  -v /host/shared:/shared/download   shared-dir=/shared/download
   aria2:     -v /host/shared:/downloads        remote-dir=/downloads
   任务目录在 aria2 侧: /downloads/offline-download/<任务id>-<UUID>
   ```

   共享目录只用于 **MAGNET / TORRENT**（HTTP / GIT 仍使用本服务 JVM 临时目录）。**shared-dir 未配置时行为与
   改造前完全一致**（要求 aria2 与下载服务同机 / 同文件系统）；配置了 `shared-dir` 但漏挂共享卷时，能力探测会报
   共享目录不存在（服务不会自动创建本地根目录）。
3. **权限对齐**：下载服务镜像以 **root** 运行；aria2 容器需使用可读写共享卷的用户（compose 示例显式
   `PUID=0` / `PGID=0`，`UMASK` 建议 `022`），否则会出现产物读取失败 / 清理失败。两容器挂载的必须是同一份
   共享存储，否则通常表现为 aria2 写入失败，或本服务读取不到 aria2 写入的产物。
4. 启动示例（令牌需与 `sfc.download.aria2.secret` 一致）：

   ```shell
   # 同机部署（RPC 仅本机可达）
   aria2c --enable-rpc --rpc-listen-all=false --rpc-listen-port=6800 \
          --rpc-secret=<令牌> --dir=<任意可写目录>

   # 跨容器 / 跨机部署（RPC 需容器网络可达；请只暴露在内网并保持令牌强度）
   aria2c --enable-rpc --rpc-listen-all=true --rpc-listen-port=6800 \
          --rpc-secret=<令牌> --dir=<共享卷内的兜底目录>
   ```

   每次任务的 `dir` 由服务端通过 RPC 强制指定为任务目录（配置共享目录时即映射后的 aria2 侧路径），
   命令行 `--dir` 仅作兜底。
5. 服务端强制项（在用户透传选项之后覆盖，保证不被用户选项覆盖）：`dir`（任务目录映射后的 aria2 侧路径）、
   `seed-time=<download.aria2.seed-time-minutes>`（做种保留时长，`0` = 不做种）、`continue=true`
   （保证 gid 丢失后同目录重新提交时可续传）、`bt-save-metadata=false`；并丢弃用户传入的 `dir` / `out` 等
   路径类选项。**刻意不设置 `follow-torrent`**：磁力链依赖 aria2 的「元数据 → 内容」自动跟进，置为 `false`
   会导致只拿到元数据、产物为空。其余透传选项受白名单限制（`split`、`max-connection-per-server`、
   `min-split-size`、`bt-tracker`、`max-download-limit`、`max-overall-download-limit`、`continue`、
   `user-agent`、`referer`；`continue` 虽在白名单内，但会被服务端强制为 `true`）。
6. aria2 内容下载不支持 SOCKS 代理：SOCKS 会被忽略并记录日志，HTTP 代理会转为 `all-proxy` 传给 aria2；
   通过 URL 提交种子任务时，`.torrent` 文件由本服务按任务代理拉取（HTTP / SOCKS 均支持），
   流式落盘并按 `download.aria2.torrent-max-size`（core 统一配置）限制体积。
7. **配置生效方式**：aria2 运行参数（开关 / 轮询间隔 / 无进度超时 / 种子体积上限 / 做种保留时长）在 core
   后台面板维护，改完最长 5 秒生效；`rpc-url` / `secret` / `shared-dir` / `remote-dir` 是部署期配置，
   修改后需重启本服务。`GET /capabilities?refresh=true` 会强制刷新统一配置快照，但 aria2 连通性探测缓存
   30 秒不变。
8. **aria2 孤儿任务对账（reconcile）**：服务启动完成后先对账一次，再按
   `sfc.download.aria2.reconcile-interval-millis`（默认 `300000` 毫秒，`<=0` 关闭）周期执行
   `Aria2OrphanReconciler`。安全边界是「只处理 `dir` 映射后位于本服务任务工作区
   （`<shared-dir>/offline-download/` 或 JVM 临时目录 `offline-download/`）下的任务」：工作区之外的 aria2
   任务**绝不触碰**；任务记录不存在只 WARN 不删除；运行中 / 等待中的任务跳过（可能等待接管）；
   做种保护目录跳过（由 `SeedTaskKeeper` 负责）。aria2 不可达时只 WARN 并跳过本轮，不置任何终态。
9. **停机与做种的关系**：aria2 是独立守护进程，本服务优雅停机**不会**停止 aria2；反之，停掉 aria2 会导致
   P2P 任务无法接管续传（重启后只能在同一目录尽力续传），做种也会中断。做种期内请保持 aria2 运行。

### 4.4 数据源切换：SQLite（默认）/ MySQL

默认行为与改造前完全一致：使用独立 SQLite `download.db` 与 Hikari 单写连接（`1/1`），无需额外数据库。
多实例部署、尤其是需要**跨实例接管 P2P 任务**时，必须切换为共享 MySQL（SQLite 文件不共享，只保证同实例重启接管）。

`application.yml` 已把全部数据源参数环境变量化（默认值保持 SQLite 行为不变）：

| yml 配置 | 环境变量 | SQLite 默认值 | MySQL 示例 |
|---|---|---|---|
| `spring.datasource.driver-class-name` | `SFC_DOWNLOAD_DB_DRIVER` | `org.sqlite.JDBC` | `com.mysql.cj.jdbc.Driver` |
| `spring.datasource.url` | `SFC_DOWNLOAD_DB_URL` | `jdbc:sqlite:./download.db?journal_mode=WAL&busy_timeout=5000` | `jdbc:mysql://127.0.0.1:3306/sfc_download?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai` |
| `spring.datasource.username` | `SFC_DOWNLOAD_DB_USERNAME` | 空 | `sfc` |
| `spring.datasource.password` | `SFC_DOWNLOAD_DB_PASSWORD` | 空 | `******` |
| `spring.datasource.hikari.maximum-pool-size` | `SFC_DOWNLOAD_DB_POOL_SIZE` | `1` | `8` |
| `spring.datasource.hikari.minimum-idle` | `SFC_DOWNLOAD_DB_MIN_IDLE` | `1` | `2` |

MySQL 环境变量示例：

```text
SFC_DOWNLOAD_DB_DRIVER=com.mysql.cj.jdbc.Driver
SFC_DOWNLOAD_DB_URL=jdbc:mysql://127.0.0.1:3306/sfc_download?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
SFC_DOWNLOAD_DB_USERNAME=sfc
SFC_DOWNLOAD_DB_PASSWORD=******
SFC_DOWNLOAD_DB_POOL_SIZE=8
SFC_DOWNLOAD_DB_MIN_IDLE=2
```

说明：

- **需提前建库**：表结构由 `spring.jpa.hibernate.ddl-auto=update` 在启动时自动创建/更新，不需要手工建表；
- **不做数据迁移**：切换数据库即使用新库，SQLite 中的历史下载任务、做种记录与 SSH 凭据不会迁移；
- **容器部署**：镜像默认 `ENV SFC_DOWNLOAD_DB_URL="jdbc:sqlite:/data/download.db?..."`（配合数据卷持久化），
  切 MySQL 时需显式覆盖 `SFC_DOWNLOAD_DB_DRIVER` 与 `SFC_DOWNLOAD_DB_URL`（以及账号/密码/连接池变量）；
- **多实例跨实例接管的前提（需全部满足）**：
  1. 共享数据库使用 MySQL（SQLite 仅保证同实例重启接管）；
  2. 各实例配置相同的 `shared-dir` / `remote-dir`，且映射到**同一份**共享存储（续传与做种依赖同一任务目录）；
  3. 各实例配置相同的 `sfc.internal.key`；
  4. 各实例注册到**同一个**注册中心（Nacos 或同一 local registry），且 core 能访问到各实例。

## 五、对外接口（经网关默认动态路由）

服务内部路径为 `/api/task/download/**`；经网关访问时前缀为 `/api/service/sfc-download-service`
（discovery locator 自动生成，无需改网关配置）：

| 方法 | 网关路径 | 说明 |
|---|---|---|
| POST | `/api/service/sfc-download-service/task/download` | 创建任务，返回任务 id（字符串）；`type` **必填**；`uid=0`（公共网盘）仅管理员可写 |
| GET | `/api/service/sfc-download-service/task/download?uid=&page=&size=&type=` | 分页列表；`type` = `ALL`/`DOWNLOADING`/`FINISH`/`FAILED`（状态筛选） |
| DELETE | `/api/service/sfc-download-service/task/download?taskId=` | 中断任务（仅创建者或管理员；仅等待中/运行中可中断） |
| GET | `/api/service/sfc-download-service/task/download/proxy` | 当前用户可见代理节点列表（前端创建表单用） |
| GET | `/api/service/sfc-download-service/task/download/capabilities?refresh=` | 下载能力声明（**需登录**，未登录 401）；每项含 `type/name/engineId/available/reason/options` |
| GET | `/api/service/sfc-download-service/task/download/detect-type?url=` | 按下载地址推断类型（**需登录**）；成功返回类型编码字符串，无法识别时 `data=null` |
| GET | `/api/service/sfc-download-service/task/download/ssh-credential` | 当前用户 SSH 凭据列表（**需登录**，返回 `id/name/authType/username/fingerprint/createAt`，不含私钥/口令/密码） |
| POST | `/api/service/sfc-download-service/task/download/ssh-credential` | 新增 SSH 凭据（**需登录**；私钥 / 带口令私钥 / 密码，返回新增后的凭据元信息） |
| DELETE | `/api/service/sfc-download-service/task/download/ssh-credential?id=` | 删除本人 SSH 凭据（**需登录**；不存在或非本人返回 400） |
| GET | `/api/service/sfc-download-service/task/download/ssh-host-key?connectAddress=` | SSH 主机信任记录列表（**管理员**；`connectAddress` 可选过滤，非管理员信封 `code=403`） |
| DELETE | `/api/service/sfc-download-service/task/download/ssh-host-key?id=` | 删除 SSH 主机信任记录（**管理员**；记录不存在返回 404） |
| GET | `/api/meta/pluginInfo` | 内部：给 core 后台的配置面板元信息，**不经网关暴露** |

创建任务的请求体：

```jsonc
{
  "type": "GIT",              // 必填：HTTP/GIT/MAGNET/TORRENT/ED2K；缺失 422，服务端不推导
  "url": "https://host/repo.git",
  "uid": 1,
  "savePath": "/download",
  "proxy": "香港节点",         // 可选，HTTP/MAGNET/TORRENT 生效，GIT 的 HTTP(S)/SSH 克隆也生效（git:// 除外）
  "sshCredentialId": "1837465928374652928", // 可选，仅 type=GIT 且为 SSH 地址时必填（凭据 id）
  "headers": {},              // 可选，仅 HTTP
  "method": "GET",            // 可选，仅 HTTP
  "connectTimeout": 10000,    // 可选，仅 HTTP
  "readTimeout": 60000,       // 可选，仅 HTTP
  "options": {                // 可选，类型专属参数
    "gitBranch": "main",
    "gitDepth": 1,
    "gitSubmodules": false,
    "stripGitDir": true,
    "gitUsername": "user",    // 私有仓库凭证，不写日志
    "gitToken": "token",
    "aria2Options": { "split": "16" }
  },
  "torrentBase64": "..."      // 可选，TORRENT 专用，与 url 二选一
}
```

SSH 凭据新增请求体（`authType=PASSWORD` 时改用 `username` + `password`，不传私钥字段）：

```jsonc
{
  "name": "GitHub 部署密钥",
  "authType": "PRIVATE_KEY",     // PRIVATE_KEY | PASSWORD
  "username": "git",             // 可选（PASSWORD 方式必填）
  "privateKey": "-----BEGIN OPENSSH PRIVATE KEY-----\n...\n-----END OPENSSH PRIVATE KEY-----",
  "passphrase": "可选：加密私钥口令"
}
```

凭据列表项 / 新增接口返回示例：
`{"id":"123","name":"GitHub 部署密钥","authType":"PRIVATE_KEY","username":"git","fingerprint":"SHA256:xxxx","createAt":"..."}`；
主机信任记录项：`{"id":"1","connectAddress":"github.com","keyType":"ssh-ed25519","publicKey":"ssh-ed25519 AAAA...","fingerprint":"SHA256:xxxx","uid":"1","createAt":"..."}`。
保存时校验：私钥必须可解析（加密私钥用口令解密）并自动计算指纹；`PASSWORD` 方式必须提供 `username` + `password`；失败返回 400。
创建 `GIT` + SSH 地址的任务时：SSH 地址未携带用户名（如 `ssh://host/path`）且私钥凭据未配置 `username` → 400
「SSH 地址未携带用户名，请在 SSH 凭据中配置用户名」（SCP 风格地址自带用户名，无需额外配置）。

响应信封 `{code, businessCode, msg, data}` 不变；列表为 `SfcPageInfo`（`content/totalCount/totalPage`）。
**ID 字段**（`id`、`taskId`、`asyncTaskRecord.id`）以及 `size/loaded/speed` 等数值按项目统一约定序列化为**字符串**
（避免 JS 64 位精度丢失，与宿主 `JacksonConfig` 一致）；`asyncTaskRecord.status` 为数字。

列表项的 `type` 为创建时提交的类型编码（`HTTP`/`GIT`/`MAGNET`/`TORRENT`/`ED2K`）；升级前创建的历史记录该列为空，
接口原样返回 `null`（不写库、不做兜底）。

列表项的 `name` 是任务展示名称：HTTP 在响应到达后先写入探测到的文件名、完成后为实际保存名；GIT 为保存的顶层目录名；
MAGNET/TORRENT 为下载文件名摘要——1 个文件即文件名，2~3 个以 `、` 连接，超过 3 个为前 3 个 + `等...`
（运行中拿到文件清单后即写入，完成时按实际下载的产物校正）。BT 多文件任务的名称仅用于展示，网盘中的真实顶层目录
仍是种子信息名，定位文件请用 `savePath` 列目录。

列表项还额外返回两个**仅用于展示、不落库**的做种字段（由 `DownloadServiceImpl#fillSeedingFields` 按
`download_seed_task` 批量回填；查询失败时回退为未做种，不影响列表接口本身）：

| 字段 | 类型 | 说明 |
|---|---|---|
| `seeding` | boolean | 是否处于做种保留中（做种中 / 清理中）；非 MAGNET/TORRENT、未开启做种、已到期清理均为 `false` |
| `seedEndAt` | string \| null | 做种到期时间（ISO-8601 时间字符串，与列表其它时间字段同格式）；仅在 `seeding=true` 时有值 |

> 两个字段是纯新增字段，旧前端忽略即可：无请求体变化、无接口路径变化，详见
> [`docs/frontend-adapter-guide.md`](docs/frontend-adapter-guide.md)。

## 六、构建与运行

```powershell
# 从仓库根 reactor 构建（推荐；根 <modules> 已包含 microservices 与本模块）
mvn -pl :sfc-download-service -am -DskipTests package

# 运行（cwd 建议放仓库外临时目录，避免 download.db 落在仓库里）
java -jar microservices/sfc-download-service/target/sfc-download-service-1.0.0.jar

# 前置：core 已启动；core 与本服务注入同一份 SFC_INTERNAL_KEY
$env:SFC_INTERNAL_KEY = '<core 的 sfc.internal.key，≥32 字符>'
```

切换 MySQL 时在启动前设置 §4.4 的 `SFC_DOWNLOAD_DB_*` 环境变量即可（默认值保持 SQLite，无需改 yml）。

浏览器/前端只经网关访问；本服务只允许内网/本机暴露（信任 `X-Sfc-Uid`，不做身份校验）。

## 七、验证要点

1. 服务注册：core `GET /api/local-registry/instances?serviceId=sfc-download-service`（Nacos 模式查控制台）可见
2. Worker：日志出现认领线程启动、`任务类型=[offline-download]`；core 的任务被本服务认领
3. 建表：首次启动后 cwd 下出现 `download.db` 且含 `download_task`（含新增的 `type` 列）、
   `download_seed_task`（做种保留记录）、`ssh_credential`（用户 SSH 凭据）、`ssh_host_key`（主机密钥信任记录）；
   切换 MySQL 后为同一批表（由 `ddl-auto=update` 自动创建）。core 侧另有 `async_task_checkpoint`（检查点表，
   通过 `task_id` 与 `async_task_record` 关联，**不改动** `async_task_record` 的表结构与实体字段）
4. 能力声明：`GET /api/task/download/capabilities` 返回 5 个类型（每项含 `engineId`），`HTTP`/`GIT` 可用，
   `MAGNET`/`TORRENT` 在 aria2 未启用时不可用且带原因，`ED2K` 恒不可用
5. 类型推断：`GET /api/task/download/detect-type?url=https%3A%2F%2Fgithub.com%2Fa%2Fb.git` 返回 `"GIT"`，
   传入无法识别的串时返回 `data=null`（仍为 `code=200`）
6. 配置面板：core 后台「插件配置」出现「离线下载」节点，含「Git 克隆」与「aria2 P2P 下载」参数分组，
   以及「SSH 凭据管理」template 菜单（组件 `download-ssh-credential-manager`）；两者的统一配置修改后
   ≤5 秒在 `capabilities` 中体现。aria2 的 `rpc-url` / `secret` / 共享目录由部署期配置
   （`sfc.download.aria2.*` / `SFC_DOWNLOAD_ARIA2_*`）控制，**修改后需重启本服务**；aria2 连通性探测另有 30 秒缓存
7. 前端资源：core `GET /api/plugin/autoLoad.js` 内容包含本服务 bundle
8. 端到端：创建下载（必须带 `type`）→ core `/api/asyncTask/listRecord` 可见 → 文件出现在目标网盘目录（重名 `.1`）→
   列表状态/进度与 core 一致 → 中断生效
9. 代理：`GET /api/task/download/proxy` 返回节点；使用无效代理名创建任务应返回 400；
   `GIT` 的 HTTP(S) 克隆与 SSH 克隆同样消费代理（HTTP 代理或 SOCKS（SOCKS5）代理），`git://` 地址带代理返回 400；
   `TORRENT` 任务带代理时，任务日志中的「开始获取种子文件」应显示所用代理并能成功取到种子
10. SSH 凭据：`POST /api/task/download/ssh-credential` 新增私钥凭据后，`GET` 列表返回
    `id/name/authType/username/fingerprint/createAt` 且**不含**私钥 / 口令 / 密码；错误的私钥、缺失口令、
    密码方式缺 `username` 应返回 400；`DELETE ?id=` 删除后列表不再出现
11. SSH 克隆：以 `ssh://host/path` 或 `git@host:owner/repo.git` + 顶层字段 `sshCredentialId` 创建 GIT 任务应克隆成功；
    不传 `sshCredentialId` → 400「SSH 地址必须选择 SSH 凭据（sshCredentialId）」；非 SSH 地址传该字段 →
    400「仅 SSH 地址支持选择 SSH 凭据」；执行前删除凭据 → 任务失败「SSH 凭据不存在或已被删除: {id}」
12. 主机信任记录：首次 SSH 克隆后 `ssh_host_key` 出现主机记录；管理员 `GET /api/task/download/ssh-host-key?connectAddress=`
    可见记录，非管理员信封 `code=403`；删除记录后再次克隆会重新执行首次信任（主机记录重新写入）
13. P2P 停机续传 / 接管：提交 MAGNET/TORRENT 任务 → 下载中优雅停止本服务（core 中任务回到等待中，`retry_count`
    不增长）→ 重新启动（或由另一实例认领，需 MySQL）→ 任务日志出现「已接管既有 aria2 任务: gid=...」，
    任务 id 不变并继续下载至完成；aria2 进程不应随本服务停止
14. 做种保留：`download.aria2.seed-time-minutes` 默认为 `10080`；P2P 任务完成后列表该行 `seeding=true` 且
    `seedEndAt` 有值，aria2 中内容任务仍在做种、本地任务目录保留；把配置改为 `0` 后**新任务**完成后立即清理
    （`seeding=false`、目录删除、aria2 结果移除）
15. 做种到期清理：把某条 `download_seed_task.seed_end_at` 调整到过去（或把保留时长调小后重启）→
    `SeedTaskKeeper` 下一轮扫描后把 `status` 置为 2（已清理）、停止做种并删除本地目录；网盘副本保留
16. aria2 孤儿对账：任务终态后手工在 aria2 留下残留结果时，启动本服务后日志出现
    「aria2 孤儿任务对账完成: 候选=…, 清理=…, 跳过=…」；工作区之外的 aria2 任务不被清理
17. 数据源切换：仅设置 §4.4 的 `SFC_DOWNLOAD_DB_*` 环境变量（不改 yml、不改代码）即可用 MySQL 启动，
    表由 `ddl-auto=update` 自动创建；默认不设置时行为与改造前一致（SQLite）

> 续传 / 接管、做种保留、aria2 对账与 MySQL 切换的逐条验收结论见
> [`docs/resume-seed-verification-report.md`](docs/resume-seed-verification-report.md)：
> 最终 **AC 13 条 = PASS 9 / PARTIAL 1 / FAIL 0 / NOTRUN 3**（NOTRUN 为 AC1-3 崩溃恢复、AC1-4 离线期间下载完成、
> AC1-6 跨实例接管（MySQL），均因时间原因未执行，可复现步骤见报告 §7）；单测最终 **166/166 通过**；
> 实测缺陷 **P5-D01**（默认做种配置下完成判定）与 **P5-D02**（种子 infohash 未落库 / 同种子复用失效）
> 均已修复并复验关闭。遗留风险与已知边界见报告 §8。

## 八、部署注意（必须执行）

1. **删除旧的插件 jar**：运行目录 `ext/sfc-ext-download.jar`（及 `release/ext-available/` 下的同名产物）必须删除/不再拷贝。
   否则 core 仍会加载旧插件 → 同一 `offline-download` 任务出现两个执行者、前端资源被重复加载。
2. **`SFC_INTERNAL_KEY` 两端一致**：core 与服务必须配置同一份密钥，否则本服务无法提交/查询/取消任务，
   也无法读取 core 的统一配置（Git 与 aria2 运行参数）；aria2 的 RPC 地址 / 令牌 / 共享目录仍来自本服务部署配置。
3. **前端 URL 手动调整**：旧路径 `/api/task/download/**` 在插件下线后会落到 core 兜底路由并 404；
   前端改用 `/api/service/sfc-download-service/task/download/**`（详见
   [`docs/frontend-adaptation-checklist.md`](docs/frontend-adaptation-checklist.md)）；
   Git 代理 + SSH 克隆（含凭据管理页与配置面板 template 组件）的完整对接说明见
   [`docs/git-proxy-ssh-clone-frontend-guide.md`](docs/git-proxy-ssh-clone-frontend-guide.md)。
4. **数据不迁移**：`xyy.db` 中的历史 `download_task` 记录不会出现在本服务；从 SQLite 切换 MySQL 时历史数据
   同样不迁移（切库即新库）。需要保留请人工导出/导入。
5. **代理语义**：创建任务时按名称解析并快照代理地址；之后修改/删除 core 代理配置不影响已创建任务。
   `ED2K` 不消费代理（传入会被清空并记录日志）；`GIT` 的 HTTP(S) 克隆与 SSH 克隆均消费代理（HTTP 代理或 SOCKS（SOCKS5）代理），
   `git://` 地址不支持代理（携带代理创建任务返回 400）。
6. **aria2 部署方式**：见 §4.3。同机部署可不配共享目录；跨容器部署必须挂载同一共享卷并配置
   `shared-dir` / `remote-dir`（漏挂时能力探测报共享目录不存在，产物读取/清理失败）。不部署 aria2 时保持
   `download.aria2.enabled=false`（core 统一配置的默认值），磁力链与种子会在创建任务阶段被 400 拒绝
   （而不是产生必然失败的异步任务）。续传/接管还要求各实例的 `shared-dir` / `remote-dir` 映射到**同一份**
   共享存储（见 §4.4）。
7. **SSH 密钥与数据库权限**：SSH 私钥 / 口令 / 密码以**明文**存放在本服务数据库，请按数据库类型限制访问权限
   （SQLite 时限制 `download.db` 文件权限、仅服务运行账号可读写；MySQL 时限制账号权限与网络可达范围），
   并按敏感数据要求做好备份/清理；接口不回显、日志已打码。本服务**不读取**宿主机
   `~/.ssh`（密钥、`known_hosts`、`config` 均不读取），SSH 认证完全由用户凭据驱动。
8. **多实例部署**：默认 SQLite 不共享，只保证**同实例重启接管** P2P 任务，跨实例接管不可用；
   SSH 凭据与主机信任记录也按实例隔离。需要多实例 / 跨实例接管时必须切换共享 MySQL（见 §4.4），并满足
   「MySQL + 相同 `shared-dir` / `remote-dir` + 相同 `sfc.internal.key` + 同一注册中心」四个前提；
   切换 MySQL 后 SSH 凭据、主机信任记录、下载任务与做种记录都随数据库共享。
9. **优雅停机窗口**：P2P 任务停机时先请求挂起，SDK 等待 `sfc.task.worker.detach-await-seconds`（默认 10 秒）
   让任务保存检查点后退出；超时才中断并兜底放弃。请使用优雅停机（`docker stop` 默认发送 SIGTERM），
   避免直接 `SIGKILL`：强杀只能依赖健康检查与检查点接管，且上传中的文件可能重复上传。
10. **做种磁盘占用**：做种保留期间本地任务目录不删除，默认保留 7 天（`download.aria2.seed-time-minutes=10080`）。
    按「下载量 × 保留时长」规划共享卷容量；不需要做种时把它设为 `0`（完成即清理，行为与改造前一致）。

## 九、已知边界与技术债

- **ed2k 未接入**：`ED2K` 为占位类型，`Ed2kDownloadEngine` 恒返回不可用，创建任务直接 400。
  接入时只需替换该引擎（可接 mldonkey / aMule），Handler / Service / Controller 均无需改动。
- **Git 支持 HTTP(S) / SSH 克隆**：SSH 支持 `ssh://host[:port]/path` 与 SCP 风格 `git@host:owner/repo.git`，
  认证方式为私钥 / 带口令（加密）私钥 / SSH 密码；SSH 凭据按用户隔离、明文存于本服务数据库，
  接口不回显、日志打码；本服务不读取宿主机 `~/.ssh`。`git://` 地址不支持代理。
- **SSH 主机密钥为 TOFU**：首次连接未知主机自动接受并写入 `ssh_host_key`（**全局共享**）；主机指纹变化会失败，
  需管理员删除旧记录后重试；首次信任写库失败时拒绝连接（fail-closed）；首个用户可抢先写入错误的主机密钥造成他人
  克隆失败（DoS，非中间人攻击），管理员删除记录即可恢复。
- **Git 子模块边界**：任务级的代理与 SSH 凭据会传递给子模块；子模块地址仅允许 `http`/`https`/`ssh`/`git`
  （`file://`、`ext::`、本地路径会被拒绝）；**HTTP(S) 父仓库 + SSH 子模块**不在支持范围；
  开启子模块且 `stripGitDir=true` 时子模块内的 `.git`（目录或 gitlink 文件）也会一并清理。
- **Git 进度为工作单元**：JGit 以 work unit 汇报进度，进度条为近似值，不是字节进度。
- **aria2 内容下载不支持 SOCKS**：SOCKS 会被 aria2 忽略并记录日志，HTTP 代理会转为 `all-proxy` 传给 aria2；
  `.torrent` 文件由本服务按任务代理拉取（HTTP / SOCKS 均支持，流式落盘并限制体积）。下载完成后默认保留
  本地文件做种 7 天（`download.aria2.seed-time-minutes`，`0` = 不做种、完成即清理）；跨容器部署依赖共享目录
  与双路径映射（见 §4.3）；未配置 `shared-dir` 时要求 aria2 与下载服务同机 / 同文件系统。
- **aria2 secret 管理**：`sfc.download.aria2.secret` 只从部署配置（环境变量 / docker secrets）读取，日志与接口
  不回显；但环境变量在 `docker inspect`、compose 文件与 shell 历史中可能可见，生产环境应使用 docker secrets
  或受控的部署配置，禁止把明文提交到仓库。
- **P2P 支持停机续传/接管（HTTP / GIT / ed2k 无续传现场）**：MAGNET / TORRENT 任务在服务优雅停机、崩溃或
  跨实例迁移后，可凭 core 中的检查点由其它执行者接管；aria2 仍在工作时继续原 gid，gid 丢失时在同目录以
  `continue=true` 尽力续传。HTTP / GIT / ed2k 没有可接管的下载现场，服务停机时按取消语义清理临时文件后
  重新排队（不写 CANCEL、不消耗重试次数），随后从头下载。
- **aria2 自身重启不做保证**：aria2 进程重启后 gid 全部丢失，只能依赖「同目录 + `continue=true`」尽力续传；
  未完成分片能否续传取决于 aria2 自身状态，不承诺结果。
- **接管次数有上限**：P2P 检查点内的 `resumeCount` 每次接管 +1，超过 50 次视为异常任务，主动上报失败并按
  收口矩阵清理，防止死循环。
- **上传阶段可能重复上传**：P2P 上传前写 `UPLOADING`、成功后写 `UPLOADED`；接管实例看到 `UPLOADED` 直接补报
  完成。但 Worker SDK 未暴露「重新读取 core 检查点」的接口，`UPLOADING` 新鲜窗口（默认 10 分钟）内只能等待，
  窗口超时后按陈旧重传，**可能产生重复文件**（由 core 落盘侧的重名策略兜底）。
- **同 infohash / 同种子复用**：做种保留期内再次提交相同磁力链（infohash 匹配）或相同种子内容
  （种子内容 SHA-256 匹配）时，直接复用做种目录中的文件上传，不再提交 aria2，避免
  `InfoHash is already registered`。仅提供 `.torrent` URL 的任务也会在提交前预取种子内容计算复用键
  （P5-D02 修复）。极端竞态（无法匹配但 aria2 已注册相同 infohash）下任务以业务错误失败：
  `该种子正在做种保留中（aria2 已注册相同 infohash），请稍后重试或等待做种保留到期`（**code=409**）。
- **做种保留时长只对新任务生效**：`download.aria2.seed-time-minutes` 在任务提交给 aria2 时读取快照；
  修改后仅影响之后创建的任务，进行中任务沿用提交时的值。到期清理只删除本地做种文件，**网盘副本保留**。
- **检查点不改变既有表结构**：检查点存放在 core 的独立表 `async_task_checkpoint`（主键 `task_id`），
  `async_task_record` 的表结构与实体字段零改动。停机挂起（detached）与**带检查点**任务的失联回收重新排队时
  不消耗 `retry_count`；无检查点任务的失联回收仍按普通故障转移累加重试次数（接管预算由检查点内的
  `resumeCount` 控制）。
- **统一配置降级**：core 不可达时 Git 与 aria2 运行参数均回落为注解默认值（Git：全量克隆、删除 `.git`；
  aria2：`enabled=false`、轮询 1000 毫秒、无进度超时 300 秒、种子上限 2 MB、做种保留 10080 分钟），
  只 WARN 一次；HTTP / Git 下载本身不受影响。aria2 的 RPC 地址 / 令牌 / 共享目录来自本服务部署配置；
  若 core 从未成功返回过配置，兜底的 `enabled=false` 会让 P2P 在 core 恢复前保持不可用。
- **落盘失败不回滚**：目录树落盘中途失败时已上传文件保留，错误信息提示已成功数量；空目录不保留。
- **SQLite 并发写**：多个任务同时收尾会并发回写 `download_task`。当前已做两层加固：
  datasource URL 启用 `journal_mode=WAL` + `busy_timeout=5000`，
  并固定 **Hikari 单写连接**（`maximum-pool-size=1`，并发创建任务时写事务在连接池排队）。
  更高并发场景建议下调 `sfc.task.worker.max-concurrent-tasks` 或按 §4.4 切换 MySQL（支持并发写，
  连接池用 `SFC_DOWNLOAD_DB_POOL_SIZE` / `SFC_DOWNLOAD_DB_MIN_IDLE` 调大）。
- **`type` 列可空**：SQLite 无法给已有数据的表直接加非空列，因此升级前创建的任务该列为空；接口原样返回 `null`，不做兜底。
- **冷启动首批并发请求**：服务刚启动时若同时收到多个依赖 Feign（core 内部接口）的请求，可能偶发
  `feign.codec.DecodeException: 'messageConverters' must not be empty`（框架首次并发初始化竞态，热态不复现）。
  建议按「core → 下载服务 → 网关」顺序启动，前端首批请求失败时重试一次即可。
- 下载临时目录：HTTP / GIT 使用服务宿主机 `java.io.tmpdir/offline-download`；MAGNET / TORRENT 在配置了
  `sfc.download.aria2.shared-dir` 时使用 `<shared-dir>/offline-download/<任务id>-<UUID>`，未配置时仍用 JVM
  临时目录。非做种任务结束即清理（HTTP / GIT 在任务结束时删除，P2P 按收口矩阵清理）；做种保留中的目录
  受保护、不参与陈旧清理。服务启动时 best-effort 清理 JVM 临时目录中超过 7 天的残留，共享工作区则在首次
  创建任务目录时清理一次超过 7 天的历史残留（清理前先排除做种保护目录，保护查询失败则跳过整轮清理）。
- 实例宕机/被外部取消等场景的本地状态镜像可能短暂漂移，列表查询校准后恢复；权威状态始终以 core 为准。
  P2P 任务在执行者失联后由 core 健康检查回收，有检查点的按「挂起」语义重新排队（不消耗重试次数）并由
  其它执行者接管，没有检查点的按普通故障转移重试（次数有限，超限失败）。
- 前端仓库的构建脚本仍把产物输出到旧插件路径（`sfc-ext/.../assert/static`），需前端侧改为输出到本服务
  `src/main/resources/static/`（或手工同步）。
