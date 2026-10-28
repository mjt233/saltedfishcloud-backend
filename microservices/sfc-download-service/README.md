# sfc-download-service —— 离线下载微服务

> 原宿主插件 `sfc-ext/sfc-ext-download` 的微服务化版本（参考先例：`microservices/sfc-ai-service` 由 `sfc-ext-ai` 迁入）。
> 插件形态已从仓库删除：不再被 `sfc-core` 以 jar 插件加载，也没有 `plugin-lib`。

## 一、定位

| 维度 | 插件期（已删除） | 微服务期（本服务） |
|---|---|---|
| 运行形态 | 与 core 同 JVM、同数据源 | 独立进程（默认端口 **8092**），独立 SQLite `download.db` |
| 任务执行 | 宿主异步任务框架 + `DownloadAsyncTaskFactory` | `sfc-task-worker` SDK，以 `SERVICE` 角色认领 `offline-download` 任务 |
| 下载能力 | 仅 HTTP/HTTPS | HTTP / GIT / MAGNET / TORRENT（+ ED2K 占位），按 `DownloadEngine` 可扩展 |
| 网盘读写 | 直接调用宿主 `DiskFileSystem` | 经 `sfc-remote-client` 回调 core 的 `/api/internal/file/v2/**` |
| 下载代理 | 直接读宿主 `proxy` 表 | 经 core 内部接口 `/api/internal/proxy/available` 解析（创建时快照） |
| 业务配置 | 宿主配置项 | **sfc-core 统一配置**（后台「插件配置」面板），本服务经 `SfcCoreConfigFeignClient` 读取 |
| 前端资源 | core 从插件 jar 的 `static/` 读取 | core 按注册中心 metadata `sfc.plugin.autoload` 从本服务 `/static/**` 拉取合并 |
| 数据 | 宿主 `xyy.db` 的 `download_task` | 本服务 `download.db`；**历史数据不迁移** |

## 二、架构与数据流

```
浏览器（主 SPA，前端 URL 由前端侧手动调整）
  │  /api/service/sfc-download-service/task/download/**     ← 网关默认动态路由（无需显式配置）
  ▼
sfc-gateway ──(discovery locator: 剥离 /api/service/{serviceId} 并补 /api)──► sfc-download-service:8092
                                                                              ├─ DownloadController        /api/task/download/**
                                                                              ├─ MetaController            /api/meta/pluginInfo
                                                                              ├─ DownloadTaskHandler       SfcTaskHandler(offline-download)
                                                                              │    └─ DownloadEngineRegistry（按 type 路由）
                                                                              │         ├─ HttpDownloadEngine     REST 流式（代理/忽略证书/超时）
                                                                              │         ├─ GitDownloadEngine      JGit 克隆 → 目录树
                                                                              │         ├─ Aria2DownloadEngine    磁力/种子（JSON-RPC）
                                                                              │         └─ Ed2kDownloadEngine     占位，恒不可用
                                                                              ├─ DownloadSaveService       单文件 / 目录树递归落盘
                                                                              ├─ SfcDownloadConfigService  统一配置读取（TTL 5s）
                                                                              └─ download.db（download_task 元数据 + 状态镜像）
                                                                                      ▲
                                    /api/internal/task/{submit,query,cancel} ─────────┤（sfc-task-worker SDK）
                                    /api/internal/proxy/available         ───────────┤（sfc-remote-client SDK）
                                    /api/internal/config/batchGet         ───────────┤（统一配置）
                                    /api/internal/file/v2/{list,mkdirs,upload} ──────┘（落盘）
                                                                                      │
                                                                                      ▼
                                                                                 sfc-core:8087
```

- **类型路由**：异步任务类型始终是 `offline-download`，`DownloadTaskHandler` 按参数里的 `type` 交给对应引擎。
  新增下载类型不需要新增任务类型，也不需要改动 Handler / Service / Controller。
- **任务状态双源**：本地 `download_task.status` 只是镜像（用于分页筛选），列表查询时对非终态任务调用
  `SfcTaskWorkerClient.queryTasks` 用 core 的真实状态/进度校准（core 不可用时 fail-soft 回退镜像）。
- **中断**：`DELETE /api/task/download?taskId=` → `SfcTaskWorkerClient.cancel` → core `AsyncTaskManager.interrupt`
  → core 按服务发现把中断指令下发到实际执行实例 → Handler 触发 `DownloadContext#cancel()`（关闭下载流 /
  `aria2.forceRemove` / 结束 Git 克隆）。
- **落盘**：下载完成后统一走 `DownloadSaveService`。单文件直接上传并按 `.1`/`.2` 规避重名；多文件或目录树
  （BT 多文件、Git 仓库）在 `savePath` 下建顶层目录（同样规避重名），再递归 `mkdirs` + 逐文件上传。
  ⚠️ **可保存的单文件大小受 core 的 `spring.servlet.multipart.max-file-size` 约束**（默认 32GiB），
  且 multipart 会先在 core 侧落临时文件再写入存储，上传期间 core 需约 2 倍文件大小的空闲磁盘；
  超过该上限时任务会以 `413 Content Too Large` 失败（需调大 core 该配置）。
- **统一配置**：aria2 / Git 参数存在 core 配置表，本服务按 5 秒 TTL 缓存读取；core 不可用时回落为
  配置实体上的注解默认值（只 WARN 一次），不影响 HTTP / Git 下载。
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
| `spring-boot-starter-data-jpa` + `sqlite-jdbc` + `hibernate-community-dialects` | 独立 SQLite 数据源 |
| `org.eclipse.jgit:org.eclipse.jgit:7.3.0.202506031305-r` | Git 克隆（纯 Java，部署机**无需**安装 git 命令行） |

## 四、配置

### 4.1 `application.yml`（部署期，修改需重启）

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `server.port` | `8092` | 服务端口（demo 8090 / ai 8091） |
| `spring.datasource.url` | `jdbc:sqlite:./download.db?journal_mode=WAL&busy_timeout=5000` | 本服务独立库；`ddl-auto=update` 自动建表。**启动 cwd 决定文件位置** |
| `spring.datasource.hikari.maximum-pool-size` / `minimum-idle` | `1` / `1` | SQLite 单写连接，避免并发写时的 `SQLITE_BUSY_SNAPSHOT` |
| `sfc.internal.key` | `${SFC_INTERNAL_KEY:}` | 服务间内部鉴权共享密钥，**必须与 core 完全一致**（≥32 字符） |
| `spring.cloud.sfc-local.*` | 见文件 | local 注册中心（默认启用） |
| `spring.cloud.nacos.discovery.*` | `enabled=false` | 启用 Nacos 时置 true 并关闭 `sfc-local` |
| `metadata.sfc.plugin.autoload` | `/static/index.umd.js,/static/style.css` | 供 core 合并前端自动加载资源（local/nacos 两处都要配） |
| `metadata.sfc.plugin.info` | `/api/meta/pluginInfo` | 供 core 后台拉取本服务的配置面板（local/nacos 两处都要配） |
| `sfc.task.worker.max-concurrent-tasks` | `8` | 并发下载槽位数（IO 密集型，按带宽/磁盘能力调整） |
| `sfc.task.worker.enabled` | `true` | 关闭后不认领任务，也不暴露中断端点 |
| `sfc.download.config.refresh-interval-millis` | `5000` | 统一配置快照缓存时长（毫秒），即后台改配置后的最长生效延迟 |
| `sfc.download.capability.cache-seconds` | `30` | 下载能力探测（aria2 连通性）结果缓存时长（秒） |

> 本服务**不读取** core 的 `sys.*` 节点，因此无需同步修改 `application-develop.yml` / `application-product.yml` /
> `pre-release/config.yml`。

### 4.2 sfc-core 统一配置（业务参数，改完无需重启）

aria2 / Git 的运行期参数不在 yml 中，统一存放在 core 的系统配置表：

- 入口：core 后台「插件配置」→「离线下载」（由注册中心 metadata `sfc.plugin.info` 发现，
  经 `GET /api/meta/pluginInfo` 渲染表单）；
- 生效：本服务按 5 秒 TTL 读取，**最长 5 秒生效**；aria2 连通性探测另有 30 秒缓存。

| 配置 key | 默认值 | 说明 |
|---|---|---|
| `download.aria2.enabled` | `false` | P2P 总开关 |
| `download.aria2.rpc-url` | `http://127.0.0.1:6800/jsonrpc` | aria2 JSON-RPC 地址 |
| `download.aria2.secret` | 空 | 与 `aria2c --rpc-secret` 一致；掩码显示且不写日志 |
| `download.aria2.poll-interval` | `1000` | 进度轮询间隔（毫秒） |
| `download.aria2.stall-timeout` | `300` | 无进度超时（秒） |
| `download.aria2.torrent-max-size` | `2` | Base64 种子内容上限（MB） |
| `download.git.default-depth` | `0` | 默认克隆深度，`0` = 全量 |
| `download.git.keep-git-dir` | `false` | 是否保留 `.git`，默认删除 |
| `download.git.connect-timeout` | `30` | JGit 连接超时（秒） |

### 4.3 aria2 部署要求

1. **必须与下载服务同机**：服务直接读取 aria2 的下载目录来上传产物，跨机不支持。
2. 启动示例（令牌与 `download.aria2.secret` 保持一致）：

   ```shell
   aria2c --enable-rpc --rpc-listen-all=false --rpc-listen-port=6800 \
          --rpc-secret=<令牌> --dir=<任意可写目录>
   ```

   每次任务的 `dir` 由服务端通过 RPC 强制指定为任务临时目录，命令行 `--dir` 仅作兜底。
3. 服务端强制 `seed-time=0`（不做种）、`bt-save-metadata=false`、`follow-torrent=false`，并丢弃用户传入的
   `dir` / `out` 等路径类选项；其余透传选项受白名单限制（`split`、`max-connection-per-server`、
   `min-split-size`、`bt-tracker`、`max-download-limit`、`max-overall-download-limit`、`continue`、
   `user-agent`、`referer`）。
4. 不支持 SOCKS 代理：任务上配置的 SOCKS 会被忽略并记录日志；HTTP 代理会转为 `all-proxy` 传给 aria2。

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
| GET | `/api/meta/pluginInfo` | 内部：给 core 后台的配置面板元信息，**不经网关暴露** |

创建任务的请求体：

```jsonc
{
  "type": "GIT",              // 必填：HTTP/GIT/MAGNET/TORRENT/ED2K；缺失 422，服务端不推导
  "url": "https://host/repo.git",
  "uid": 1,
  "savePath": "/download",
  "proxy": "香港节点",         // 可选，仅 HTTP/MAGNET/TORRENT 生效
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

响应信封 `{code, businessCode, msg, data}` 不变；列表为 `SfcPageInfo`（`content/totalCount/totalPage`）。
**ID 字段**（`id`、`taskId`、`asyncTaskRecord.id`）以及 `size/loaded/speed` 等数值按项目统一约定序列化为**字符串**
（避免 JS 64 位精度丢失，与宿主 `JacksonConfig` 一致）；`asyncTaskRecord.status` 为数字。

列表项的 `type` 为创建时提交的类型编码（`HTTP`/`GIT`/`MAGNET`/`TORRENT`/`ED2K`）；升级前创建的历史记录该列为空，
接口原样返回 `null`（不写库、不做兜底）。

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
2. Worker：日志出现认领线程启动、`任务类型=[offline-download]`；core 的任务被本服务认领
3. 建表：首次启动后 cwd 下出现 `download.db` 且含 `download_task`（含新增的 `type` 列）
4. 能力声明：`GET /api/task/download/capabilities` 返回 5 个类型（每项含 `engineId`），`HTTP`/`GIT` 可用，
   `MAGNET`/`TORRENT` 在 aria2 未启用时不可用且带原因，`ED2K` 恒不可用
5. 类型推断：`GET /api/task/download/detect-type?url=https%3A%2F%2Fgithub.com%2Fa%2Fb.git` 返回 `"GIT"`，
   传入无法识别的串时返回 `data=null`（仍为 `code=200`）
6. 配置面板：core 后台「插件配置」出现「离线下载」节点，含 aria2 / Git 两组参数；
   修改后 ≤5 秒在 `capabilities` 中体现（注意 aria2 连通性探测另有 30 秒缓存）
7. 前端资源：core `GET /api/plugin/autoLoad.js` 内容包含本服务 bundle
8. 端到端：创建下载（必须带 `type`）→ core `/api/asyncTask/listRecord` 可见 → 文件出现在目标网盘目录（重名 `.1`）→
   列表状态/进度与 core 一致 → 中断生效
9. 代理：`GET /api/task/download/proxy` 返回节点；使用无效代理名创建任务应返回 400

## 八、部署注意（必须执行）

1. **删除旧的插件 jar**：运行目录 `ext/sfc-ext-download.jar`（及 `release/ext-available/` 下的同名产物）必须删除/不再拷贝。
   否则 core 仍会加载旧插件 → 同一 `offline-download` 任务出现两个执行者、前端资源被重复加载。
2. **`SFC_INTERNAL_KEY` 两端一致**：core 与服务必须配置同一份密钥，否则本服务无法提交/查询/取消任务，
   也无法读取统一配置。
3. **前端 URL 手动调整**：旧路径 `/api/task/download/**` 在插件下线后会落到 core 兜底路由并 404；
   前端改用 `/api/service/sfc-download-service/task/download/**`（详见
   [`docs/frontend-adaptation-checklist.md`](docs/frontend-adaptation-checklist.md)）。
4. **数据不迁移**：`xyy.db` 中的历史 `download_task` 记录不会出现在本服务；需要保留请人工导出/导入。
5. **代理语义**：创建任务时按名称解析并快照代理地址；之后修改/删除 core 代理配置不影响已创建任务。
   `GIT` / `ED2K` 类型不消费代理，传入会被清空并记录日志。
6. **aria2 同机部署**：见 §4.3，跨机不工作；不部署 aria2 时保持 `download.aria2.enabled=false`，
   磁力链与种子会在创建任务阶段被 400 拒绝（而不是产生必然失败的异步任务）。

## 九、已知边界与技术债

- **ed2k 未接入**：`ED2K` 为占位类型，`Ed2kDownloadEngine` 恒返回不可用，创建任务直接 400。
  接入时只需替换该引擎（可接 mldonkey / aMule），Handler / Service / Controller 均无需改动。
- **Git 只支持 http/https/git://**：SSH 传输需额外引入 `org.eclipse.jgit.ssh.apache`，本期未做；
  `ssh://` 与 `git@host:path` 会被明确拒绝。
- **Git 进度为工作单元**：JGit 以 work unit 汇报进度，进度条为近似值，不是字节进度。
- **aria2 必须同机且不支持 SOCKS**；不做种（`seed-time=0`），下载完成即释放连接。
- 任务执行**不支持断点续传/自动重试**：失败即失败；core 的故障转移重试会从头重新下载。
- **统一配置降级**：core 不可达时回落为注解默认值（aria2 关闭、Git 全量克隆、删除 `.git`），
  只 WARN 一次；core 故障期间 P2P 类型会被判定不可用。
- **落盘失败不回滚**：目录树落盘中途失败时已上传文件保留，错误信息提示已成功数量；空目录不保留。
- **SQLite 并发写**：多个任务同时收尾会并发回写 `download_task`。当前已做两层加固：
  datasource URL 启用 `journal_mode=WAL` + `busy_timeout=5000`，
  并固定 **Hikari 单写连接**（`maximum-pool-size=1`，并发创建任务时写事务在连接池排队）。
  更高并发场景建议下调 `sfc.task.worker.max-concurrent-tasks` 或改用更高并发的数据库实现。
- **`type` 列可空**：SQLite 无法给已有数据的表直接加非空列，因此升级前创建的任务该列为空；接口原样返回 `null`，不做兜底。
- **冷启动首批并发请求**：服务刚启动时若同时收到多个依赖 Feign（core 内部接口）的请求，可能偶发
  `feign.codec.DecodeException: 'messageConverters' must not be empty`（框架首次并发初始化竞态，热态不复现）。
  建议按「core → 下载服务 → 网关」顺序启动，前端首批请求失败时重试一次即可。
- 下载临时文件落在服务宿主机 `java.io.tmpdir/offline-download`，任务结束清理；启动时 best-effort 清理 7 天前残留。
- 实例宕机/被外部取消等场景的本地状态镜像可能短暂漂移，列表查询校准后恢复；权威状态始终以 core 为准。
- 前端仓库的构建脚本仍把产物输出到旧插件路径（`sfc-ext/.../assert/static`），需前端侧改为输出到本服务
  `src/main/resources/static/`（或手工同步）。
