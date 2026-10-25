# sfc-download-service —— 离线下载微服务

> 原宿主插件 `sfc-ext/sfc-ext-download` 的微服务化版本（参考先例：`microservices/sfc-ai-service` 由 `sfc-ext-ai` 迁入）。
> 插件形态已从仓库删除：不再被 `sfc-core` 以 jar 插件加载，也没有 `plugin-lib`。

## 一、定位

| 维度 | 插件期（已删除） | 微服务期（本服务） |
|---|---|---|
| 运行形态 | 与 core 同 JVM、同数据源 | 独立进程（默认端口 **8092**），独立 SQLite `download.db` |
| 任务执行 | 宿主异步任务框架 + `DownloadAsyncTaskFactory` | `sfc-task-worker` SDK，以 `SERVICE` 角色认领 `offline-download` 任务 |
| 网盘读写 | 直接调用宿主 `DiskFileSystem` | 经 `sfc-remote-client` 回调 core 的 `/api/internal/file/v2/**` |
| 下载代理 | 直接读宿主 `proxy` 表 | 经 core 新增的内部接口 `/api/internal/proxy/available` 解析（创建时快照） |
| 前端资源 | core 从插件 jar 的 `static/` 读取 | core 按注册中心 metadata `sfc.plugin.autoload` 从本服务 `/static/**` 拉取合并 |
| 数据 | 宿主 `xyy.db` 的 `download_task` | 本服务 `download.db`；**历史数据不迁移** |

## 二、架构与数据流

```
浏览器（主 SPA，前端 URL 由前端侧手动调整）
  │  /api/service/sfc-download-service/task/download/**     ← 网关默认动态路由（无需显式配置）
  ▼
sfc-gateway ──(discovery locator: 剥离 /api/service/{serviceId} 并补 /api)──► sfc-download-service:8092
                                                                              ├─ DownloadController   /api/task/download/**
                                                                              ├─ DownloadTaskHandler  SfcTaskHandler(offline-download)
                                                                              │     ├─ HTTP 下载 → 本地临时文件（代理/忽略证书/超时/进度/中断）
                                                                              │     └─ 落盘 → core /api/internal/file/v2/upload
                                                                              └─ download.db（download_task 元数据 + 状态镜像）
                                                                                      ▲
                                        /api/internal/task/{submit,query,cancel} ─────┘（sfc-task-worker SDK）
                                        /api/internal/proxy/available         ───────┘（sfc-remote-client SDK）
                                                                              │
                                                                              ▼
                                                                         sfc-core:8087
```

- **任务状态双源**：本地 `download_task.status` 只是镜像（用于分页筛选），列表查询时对非终态任务调用
  `SfcTaskWorkerClient.queryTasks` 用 core 的真实状态/进度校准（core 不可用时 fail-soft 回退镜像）。
- **中断**：`DELETE /api/task/download?taskId=` → `SfcTaskWorkerClient.cancel` → core `AsyncTaskManager.interrupt`
  → core 按服务发现把中断指令下发到实际执行实例。
- **文件落盘**：下载完成后以流式 multipart 上传到 core（`SfcCoreStreamFileClient`），MD5 由 core 计算；
  同名文件由本服务先查 core 的目录列表并按旧规则追加 `.1`、`.2`。
- **前端资源**：core 的 `GET /api/plugin/autoLoad.js` 会把本服务 `/static/index.umd.js`、`/static/style.css`
  合并返回（与插件期行为一致，前端加载链路不变）。

## 三、依赖

| 依赖 | 用途 |
|---|---|
| `com.sfc.cloud:sfc-common` / `sfc-common-data` | 响应信封/异常/工具/分页 + JPA 基座（`SfcAuditModel`/`SfcBaseRepo`/`@SfcUID`） |
| `com.sfc.cloud:sfc-remote-client` | 回调 core 的网盘文件接口与代理查询接口 |
| `com.sfc.cloud:sfc-task-worker` | 异步任务执行者 SDK（认领/执行/进度/日志/中断/停机） |
| `com.sfc.cloud:sfc-local-registry-client` | 未启用 Nacos 时接入 core 的本地注册中心 |
| `spring-cloud-starter-alibaba-nacos-discovery` | 启用 Nacos 时注册/发现 |
| `spring-boot-starter-data-jpa` + `sqlite-jdbc` + `hibernate-community-dialects` | 独立 SQLite 数据源 |

## 四、配置（`src/main/resources/application.yml`）

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `server.port` | `8092` | 服务端口（demo 8090 / ai 8091） |
| `spring.datasource.url` | `jdbc:sqlite:./download.db?journal_mode=WAL&busy_timeout=5000` | 本服务独立库；`ddl-auto=update` 自动建表。**启动 cwd 决定文件位置**。WAL + busy_timeout 用于避免多任务并发落盘回写时的 `SQLITE_BUSY`（见 §九） |
| `spring.datasource.hikari.maximum-pool-size` / `minimum-idle` | `1` / `1` | SQLite 单写连接：并发写请求在连接池内排队，避免并发创建任务时 `SQLITE_BUSY_SNAPSHOT`（Hibernate merge 先 SELECT 再 UPDATE 的快照升级冲突，busy_timeout 不覆盖）。改用 MySQL 等并发数据库时移除该限制 |
| `sfc.internal.key` | `${SFC_INTERNAL_KEY:}` | 服务间内部鉴权共享密钥，**必须与 core 完全一致**（≥32 字符），否则任务提交/查询/取消全部失败 |
| `spring.cloud.sfc-local.*` | 见文件 | local 注册中心（默认启用；`server-addr` 指向 core，密码需与 core `sys.service.local-registry.password` 一致） |
| `spring.cloud.nacos.discovery.*` | `enabled=false` | 启用 Nacos 时置 true 并关闭 `sfc-local`；`group=SFC`、`namespace=""`、`ephemeral=true` |
| `metadata.sfc.plugin.autoload` | `/static/index.umd.js,/static/style.css` | 供 core 合并前端自动加载资源（local/nacos 两处都要配） |
| `sfc.task.worker.max-concurrent-tasks` | `8` | 并发下载槽位数（IO 密集型，按带宽/磁盘能力调整） |
| `sfc.task.worker.enabled` | `true` | 关闭后不认领任务，也不暴露中断端点 |

> 本服务不需要、也不读取 core 的 `sys.*` 配置节点，因此无需同步修改 `application-develop.yml` / `application-product.yml` / `pre-release/config.yml`。

## 五、对外接口（经网关默认动态路由）

服务内部路径保持与旧插件逐字一致 `/api/task/download/**`；经网关访问时前缀为
`/api/service/sfc-download-service`（discovery locator 自动生成，无需改网关配置）：

| 方法 | 网关路径 | 说明 |
|---|---|---|
| POST | `/api/service/sfc-download-service/task/download` | 创建任务，返回任务 id（字符串）。请求体：`{url, uid, savePath, headers?, method?, proxy?, connectTimeout?, readTimeout?}`；`uid=0`（公共网盘）仅管理员可写 |
| GET | `/api/service/sfc-download-service/task/download?uid=&page=&size=&type=` | 分页列表；`type` = `ALL`/`DOWNLOADING`/`FINISH`/`FAILED` |
| DELETE | `/api/service/sfc-download-service/task/download?taskId=` | 中断任务（仅创建者或管理员；仅等待中/运行中可中断） |
| GET | `/api/service/sfc-download-service/task/download/proxy` | 当前用户可见代理节点列表（前端创建表单用） |

响应信封 `{code, businessCode, msg, data}` 不变；列表为 `SfcPageInfo`（`content/totalCount/totalPage`）。
**ID 字段**（`id`、`taskId`、`asyncTaskRecord.id`）以及 `size/loaded/speed` 等数值按项目统一约定序列化为**字符串**（避免 JS 64 位精度丢失，与宿主 `JacksonConfig` 一致）；`asyncTaskRecord.status` 为数字。

## 六、构建与运行

```powershell
# 从仓库根 reactor 构建（推荐；根 <modules> 已包含 microservices 与本模块）
mvn -pl :sfc-download-service -am -DskipTests package

# 运行（cwd 建议放仓库外临时目录，避免 download.db 落在仓库里）
java -jar microservices/sfc-download-service/target/sfc-download-service-1.0.0.jar

# 前置：core 已启动；core 与本服务注入同一份 SFC_INTERNAL_KEY
$env:SFC_INTERNAL_KEY = '<core 的 sfc.internal.key，≥32 字符>'
```

浏览器/前端只经网关访问；本服务只允许内网/本机暴露（信任 `X-Sfc-Uid`，不做身份校验）。

## 七、验证要点

1. 服务注册：core `GET /api/local-registry/instances?serviceId=sfc-download-service`（Nacos 模式查控制台）可见
2. Worker：日志出现认领线程启动、`任务类型=[offline-download]`；core 的任务被本服务认领（`claimedService=sfc-download-service`、`claimedRole=SERVICE`）
3. 建表：首次启动后 cwd 下出现 `download.db` 且含 `download_task`
4. 前端资源：core `GET /api/plugin/autoLoad.js` 内容包含本服务 bundle
5. 端到端：创建下载 → core `/api/asyncTask/listRecord` 可见 → 文件出现在目标网盘目录（重名 `.1`）→ 列表状态/进度与 core 一致 → 中断生效
6. 代理：`GET /api/task/download/proxy` 返回节点；使用无效代理名创建任务应返回 400

## 八、部署注意（必须执行）

1. **删除旧的插件 jar**：运行目录 `ext/sfc-ext-download.jar`（及 `release/ext-available/` 下的同名产物）必须删除/不再拷贝。
   否则 core 仍会加载旧插件 → 同一 `offline-download` 任务出现两个执行者、前端资源被重复加载。
2. **`SFC_INTERNAL_KEY` 两端一致**：core 与服务必须配置同一份密钥，否则本服务无法提交/查询/取消任务。
3. **前端 URL 手动调整**：旧路径 `/api/task/download/**` 在插件下线后会落到 core 兜底路由并 404；
   前端改用 `/api/service/sfc-download-service/task/download/**`（详见
   [`docs/frontend-adaptation-checklist.md`](docs/frontend-adaptation-checklist.md)）。
4. **数据不迁移**：`xyy.db` 中的历史 `download_task` 记录不会出现在本服务；需要保留请人工导出/导入。
5. **代理语义**：创建任务时按名称解析并快照代理地址；之后修改/删除 core 代理配置不影响已创建任务。

## 九、已知边界与技术债

- 任务执行**不支持断点续传/自动重试**：与旧插件一致，失败即失败；core 的故障转移重试会从头重新下载。
- **SQLite 并发写**：多个下载任务同时收尾会并发回写 `download_task`。当前已做两层加固：
  datasource URL 启用 `journal_mode=WAL` + `busy_timeout=5000`（执行期并发落盘），
  并固定 **Hikari 单写连接**（`maximum-pool-size=1`，并发创建任务时写事务在连接池排队，避免 `SQLITE_BUSY_SNAPSHOT`）。
  若部署在低配磁盘或更高并发场景，建议下调 `sfc.task.worker.max-concurrent-tasks` 或改用更高并发的数据库实现。
- **冷启动首批并发请求**：服务刚启动时若同时收到多个依赖 Feign（core 内部接口）的请求，可能偶发
  `feign.codec.DecodeException: 'messageConverters' must not be empty`（框架首次并发初始化竞态，热态不复现）。
  建议按「core → 下载服务 → 网关」顺序启动，前端首批请求失败时重试一次即可。
- 下载临时文件落在服务宿主机 `java.io.tmpdir/offline-download`，任务结束清理；启动时 best-effort 清理 7 天前残留。
- 实例宕机/被外部取消等场景的本地状态镜像可能短暂漂移，列表查询校准后恢复；权威状态始终以 core 为准。
- 前端仓库的构建脚本仍把产物输出到旧插件路径（`sfc-ext/.../assert/static`），需前端侧改为输出到本服务
  `src/main/resources/static/`（或手工同步）。
