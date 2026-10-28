# 离线下载 download（微服务版）

## 简介

离线下载能力已从「宿主插件」改造为**独立微服务** `microservices/sfc-download-service`（原 `sfc-ext-download` 插件已从仓库删除）。
服务把多种下载来源的产物保存到用户网盘的指定目录：HTTP/HTTPS 直链、Git 仓库克隆、磁力链与种子（经 aria2），
同时提供代理节点、实时进度、任务中断与下载能力声明。

下载执行与「下载类型」解耦：每种类型由一个 `DownloadEngine` 实现承载，新增下载方式只需实现一个引擎。

- 服务端口：`8092`
- 数据存储：本服务独立 SQLite `download.db`（历史插件数据在宿主 `xyy.db`，**不迁移**）
- 任务执行：以 `SERVICE` 角色接入 `sfc-core` 的统一异步任务系统；任务类型仍是单一 `offline-download`，
  下载类型在本服务内部按 `type` 路由到对应引擎
- 访问入口：经 `sfc-gateway` 的默认动态路由 `/api/service/sfc-download-service/task/download/**`
- 部署：`microservices/sfc-download-service/README.md`
- 前端适配：`microservices/sfc-download-service/docs/frontend-adaptation-checklist.md`
- 微服务总览：`docs/develop/microservice/index.md`

## 下载类型

| 类型 | 实现 | 外部依赖 | 说明 |
|---|---|---|---|
| `HTTP` | 内置流式 HTTP 下载 | 无 | 支持自定义请求头 / 请求方法 / 超时 / 代理，并忽略服务端证书校验 |
| `GIT` | JGit `7.3.0.202506031305-r`（纯 Java） | 无 | 支持 `http` / `https` / `git://`；**不支持 `ssh://` 与 `git@host:path`** |
| `MAGNET` | aria2 JSON-RPC | 需同机运行 `aria2c` | 磁力链 |
| `TORRENT` | aria2 JSON-RPC | 需同机运行 `aria2c` | `.torrent` 文件地址，或 Base64 种子内容 |
| `ED2K` | 占位引擎 | — | 已登记类型但引擎未接入：能力声明中恒为不可用，创建任务直接返回 400 |

**类型必填**。创建任务时必须显式提交 `type`，服务端不在提交或执行阶段推导、也不降级到其它类型
（缺失 → HTTP 422，`AUTO` 等未定义取值 → HTTP 400）。前端需要「按地址自动选型」时，先调用
`GET /api/task/download/detect-type?url=` 取得候选类型（纯语法判断，无法识别时返回 `data=null`），再随请求提交。
推断能力由各 `DownloadEngine` 的 `matchesSource(type, source)` 承担，注册表按
`MAGNET → ED2K → GIT → TORRENT → HTTP` 的顺序取第一个命中的类型：

| 来源特征 | 推断结果 |
|---|---|
| `magnet:?xt=...` | `MAGNET` |
| `ed2k://...` | `ED2K` |
| `git://...`、`ssh://...`、`git@host:path`、http(s) 且路径以 `.git` 结尾 | `GIT`（其中 `ssh` 形式会在创建任务时以 400 拒绝） |
| http(s) 且路径以 `.torrent` 结尾 | `TORRENT` |
| 其余 http(s) | `HTTP` |
| 空 / 无法判定 | `null`（接口不报错） |

> 仅提供 `torrentBase64`、没有 `url` 的任务不是「推断」场景：前端已选择 `TORRENT`，直接提交即可。

## 主要功能

- 五种下载类型（四种可用 + ed2k 占位），**类型必填**，并提供按下载地址推断类型的辅助接口
- HTTP 代理：创建任务时按代理名称从 core 解析并快照地址/端口；**仅 `HTTP` / `MAGNET` / `TORRENT` 消费代理，
  `GIT` / `ED2K` 会忽略并清空代理快照**
- 实时进度（速度、已下载量、总大小）与任务状态，可中断等待中 / 运行中的任务
- 按用户分页查询任务列表，并可按状态筛选（ALL / DOWNLOADING / FINISH / FAILED）
- 落盘自动规避重名：单文件追加 `.1`、`.2`；目录树对顶层目录名同样做规避
- 多文件 / 目录树产物（BT 多文件、Git 仓库）按原目录结构递归创建目录并逐文件上传
- 下载能力声明接口：返回每种类型是否可用、不可用原因、承载引擎标识（`engineId`）以及可选项元数据
- 业务配置由 sfc-core 统一管理，管理员在后台修改后无需重启本服务
- 前端插件资源（`index.umd.js` / `style.css`）随服务发布，仍由 `sfc-core` 的 `plugin/autoLoad.js` 合并加载

## 统一配置

aria2 / Git 的运行期参数**不在**本服务的 `application.yml` 中，统一存放在 `sfc-core` 的系统配置表：

- 管理员在 core 后台的「插件配置」面板（节点名「离线下载」）修改；
- 本服务通过 `SfcCoreConfigFeignClient` 读取，快照缓存 5 秒，**修改后最长 5 秒生效，无需重启**；
- aria2 的连通性探测另有 30 秒缓存（见 `sfc.download.capability.cache-seconds`），刚改完开关时可能需要等待其过期。

| 配置 key | 默认值 | 说明 |
|---|---|---|
| `download.aria2.enabled` | `false` | P2P 总开关；关闭时磁力链与种子在创建任务阶段即被 400 拒绝 |
| `download.aria2.rpc-url` | `http://127.0.0.1:6800/jsonrpc` | aria2 JSON-RPC 地址 |
| `download.aria2.secret` | 空 | 与 `aria2c --rpc-secret` 一致；后台掩码显示，日志与接口不回显 |
| `download.aria2.poll-interval` | `1000` | 进度轮询间隔（毫秒） |
| `download.aria2.stall-timeout` | `300` | 无进度超时（秒）：连续该时长已下载字节无增长即判定失败 |
| `download.aria2.torrent-max-size` | `2` | Base64 种子内容上限（MB） |
| `download.git.default-depth` | `0` | 默认克隆深度，`0` 表示全量克隆 |
| `download.git.keep-git-dir` | `false` | 是否保留 `.git`；默认删除（网盘场景只需要工作区文件） |
| `download.git.connect-timeout` | `30` | JGit 连接超时（秒） |

`application.yml` 只保留部署期 / 内部调优参数：端口、数据源、`sfc.internal.key`、Worker 并发、注册中心，
以及 `sfc.download.config.refresh-interval-millis`（默认 5000）与 `sfc.download.capability.cache-seconds`（默认 30）。

> 刻意不做「yml 覆盖统一配置」：业务参数只有一处数据源，避免排障时无法判断哪份配置生效。

**降级行为**：core 不可用时，配置读取回落为配置实体上的注解默认值（即上表默认值），
并只打一条 WARN；不会阻塞服务启动，也不影响不依赖这些参数的 HTTP / Git 下载。

## aria2 部署要求

磁力链与种子依赖外部 aria2 进程，部署前需确认以下三点：

1. **必须与下载服务同机**。服务在下载完成后直接通过文件系统读取 aria2 的下载目录来上传产物，
   跨机部署（aria2 在另一台机器）不支持。
2. 启动示例（令牌需与 `download.aria2.secret` 一致）：

   ```shell
   aria2c --enable-rpc --rpc-listen-all=false --rpc-listen-port=6800 \
          --rpc-secret=<与配置一致的令牌> --dir=<任意可写目录>
   ```

   命令行里的 `--dir` 只是兜底：**每次任务的下载目录由服务端通过 RPC 选项强制指定**为该任务的本地临时目录。
3. 服务端会强制覆盖以下选项，用户传入同名项会被丢弃：`dir`、`seed-time=0`（下载完成即停止做种）、
   `bt-save-metadata=false`、`follow-torrent=false`。

用户可通过任务的 `options.aria2Options` 透传选项，取值为 JSON 对象文本（键为 aria2 选项名、值统一按字符串），
例如 `{"split":"16","max-connection-per-server":"16"}`；服务端仅放行白名单：
`split`、`max-connection-per-server`、`min-split-size`、`bt-tracker`、`max-download-limit`、
`max-overall-download-limit`、`continue`、`user-agent`、`referer`。

## 对外接口（经网关默认动态路由）

服务内部路径为 `/api/task/download/**`；经网关访问时前缀为 `/api/service/sfc-download-service`
（discovery locator 自动生成，无需改网关配置）：

| 方法 | 网关路径 | 说明 |
|---|---|---|
| POST | `/api/service/sfc-download-service/task/download` | 创建任务，返回任务 id（字符串）；`type` 必填 |
| GET | `/api/service/sfc-download-service/task/download?uid=&page=&size=&type=` | 分页列表；`type` = `ALL`/`DOWNLOADING`/`FINISH`/`FAILED`（状态筛选） |
| DELETE | `/api/service/sfc-download-service/task/download?taskId=` | 中断任务（仅创建者或管理员；仅等待中/运行中可中断） |
| GET | `/api/service/sfc-download-service/task/download/proxy` | 当前用户可见的代理节点列表 |
| GET | `/api/service/sfc-download-service/task/download/capabilities?refresh=` | 下载能力声明（需登录） |
| GET | `/api/service/sfc-download-service/task/download/detect-type?url=` | 按下载地址推断类型（需登录）；无法识别时 `data=null` |

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
  "options": {                // 可选，类型专属参数
    "gitBranch": "main",
    "gitDepth": 1,
    "gitSubmodules": false,
    "stripGitDir": true
  },
  "torrentBase64": "..."      // 可选，TORRENT 专用，与 url 二选一
}
```

能力声明返回每项的 `type` / `name` / `engineId` / `available` / `reason` / `options[]`，可直接用于前端渲染类型下拉与参数表单。
`engineId` 是承载该类型的引擎标识（`http` / `git` / `aria2` / `ed2k`），仅用于诊断/埋点，不参与类型判断。
`options[]` 的每个元素是一个 `ConfigNode`（`com.sfc.common.model.ConfigNode`，与核心配置面板同一套表单节点模型）：
`name` 为选项键（提交时放进请求体的 `options`）、`title` 为展示名、`inputType` 决定输入控件
（`text` / `textarea` / `switch`）、`defaultValue` 为字符串形式的默认值、`describe` 为说明文案、
`mask` 表示凭证类选项需打码、`sort` 决定展示顺序。
`refresh=true` 会先强制刷新统一配置快照，用于管理员刚改完 aria2 开关后立即验证。

列表项的 `type` 为创建时提交的类型编码；历史记录（升级前创建、该列为空）原样返回 `null`。

## 如何新增一种下载类型

1. 在 `DownloadType` 中增加枚举值（含展示名 `displayName`）；
2. 实现 `DownloadEngine`（必须实现 `getEngineId()` / `supports()` / `validate()` / `execute()`，可选覆盖
   `matchesSource()` / `unavailableReason()` / `options()` / `taskName()` / `cpuOverhead()`），并标注 `@Component`；
   `getEngineId()` 必须全局唯一，重复会导致启动失败；
3. 若该类型需要被 `detect-type` 自动识别，覆盖 `matchesSource(type, source)` 声明其来源语法特征，并在
   `DownloadSourceResolver` 的 `validateSource()` 中补充创建期的协议校验；
4. （可选）若该类型有专属参数键，把它们加到 `DownloadOptionKeys`；
5. 跑构建，并用 `GET /api/task/download/capabilities` 验证新类型是否出现、可用性、`engineId` 与选项元数据是否正确。

**不需要**修改任务处理器、Service 或 Controller：任务类型仍是 `offline-download`，路由由
`DownloadEngineRegistry` 完成。占位实现可参考 `Ed2kDownloadEngine`。

## 已知问题与说明

- **ed2k 未接入**：`ED2K` 只是登记了类型，引擎为占位实现，创建任务直接返回 400；后续接入点见
  `Ed2kDownloadEngine` 类注释（可接 mldonkey / aMule）。
- **Git 不支持 SSH**：JGit 的 SSH 传输需要额外的 `org.eclipse.jgit.ssh.apache` 依赖，本期未引入，
  因此仅支持 `http/https/git://`；`ssh://` 与 `git@host:path` 会在创建任务时被明确拒绝。
- **Git 进度是近似值**：JGit 以「工作单元」而非字节汇报进度，进度条按工作单元累加，不能当作字节进度使用。
- **aria2 必须同机**，且**不支持 SOCKS 代理**：任务上配置的 SOCKS 代理会被忽略并记录日志；HTTP 代理会转为
  `all-proxy` 传给 aria2。
- **任务不重试 / 不续传**：执行失败即失败（是否重新排队由异步任务框架的故障转移策略决定，重跑会从头下载）。
- **落盘失败不回滚**：目录树落盘过程中若某个文件上传失败，已上传成功的文件不会删除，失败信息会提示已成功数量。
  空目录不会保留（上传只处理文件）。
- **单文件大小受限**：仍受 core 的 `spring.servlet.multipart.max-file-size`（默认 32GiB）约束，超限会以
  `413 Content Too Large` 失败，需要调大 core 该配置。
- **配置降级**：core 不可用时统一配置回落为注解默认值（aria2 关闭、Git 全量克隆、删除 `.git`），
  因此 core 故障期间磁力链/种子任务会被拒绝；HTTP 与 Git 下载本身不受影响。
- **代理契约**：旧版前端提交的是代理「名称」，服务端按名称解析并快照地址/端口；之后修改/删除 core 的代理配置
  不影响已创建任务。
- **历史数据不迁移**：插件期下载记录在宿主 `xyy.db`，微服务使用独立 `download.db`，不会自动出现。
- 本服务信任网关注入的 `X-Sfc-Uid`，只允许内网 / 本机暴露，对外入口只有网关。
