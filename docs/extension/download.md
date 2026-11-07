# 离线下载 download（微服务版）

## 简介

离线下载能力已从「宿主插件」改造为**独立微服务** `microservices/sfc-download-service`（原 `sfc-ext-download` 插件已从仓库删除）。
服务把多种下载来源的产物保存到用户网盘的指定目录：HTTP/HTTPS 直链、Git 仓库克隆（支持 HTTP(S) 与 SSH）、磁力链与种子（经 aria2），
同时提供代理节点、实时进度、任务中断与下载能力声明。

下载执行与「下载类型」解耦：每种类型由一个 `DownloadEngine` 实现承载，新增下载方式只需实现一个引擎。

- 服务端口：`8092`
- 数据存储：本服务独立 SQLite `download.db`（历史插件数据在宿主 `xyy.db`，**不迁移**）
- 任务执行：以 `SERVICE` 角色接入 `sfc-core` 的统一异步任务系统；任务类型仍是单一 `offline-download`，
  下载类型在本服务内部按 `type` 路由到对应引擎
- 访问入口：经 `sfc-gateway` 的默认动态路由 `/api/service/sfc-download-service/task/download/**`
- 部署：`microservices/sfc-download-service/README.md`
- 前端适配：`microservices/sfc-download-service/docs/frontend-adaptation-checklist.md`；
  Git 代理 + SSH 克隆专项对接说明：`microservices/sfc-download-service/docs/git-proxy-ssh-clone-frontend-guide.md`
- 微服务总览：`docs/develop/microservice/index.md`

## 下载类型

| 类型 | 实现 | 外部依赖 | 说明 |
|---|---|---|---|
| `HTTP` | 内置流式 HTTP 下载 | 无 | 支持自定义请求头 / 请求方法 / 超时 / 代理，并忽略服务端证书校验 |
| `GIT` | JGit `7.3.0.202506031305-r`（纯 Java；SSH 传输由 `org.eclipse.jgit.ssh.apache` 提供） | 无 | 支持 `http` / `https` / `git://` / `ssh`；SSH 克隆需提供用户 SSH 凭据，`git://` 不支持代理 |
| `MAGNET` | aria2 JSON-RPC | 需可访问 aria2 JSON-RPC，并与下载服务共享下载目录 | 磁力链 |
| `TORRENT` | aria2 JSON-RPC | 需可访问 aria2 JSON-RPC，并与下载服务共享下载目录 | `.torrent` 文件地址，或 Base64 种子内容 |
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
| `git://...`、`ssh://...`、`git@host:path`、http(s) 且路径以 `.git` 结尾 | `GIT`（SSH 形式创建任务时必须同时提供 `sshCredentialId`） |
| http(s) 且路径以 `.torrent` 结尾 | `TORRENT` |
| 其余 http(s) | `HTTP` |
| 空 / 无法判定 | `null`（接口不报错） |

> 仅提供 `torrentBase64`、没有 `url` 的任务不是「推断」场景：前端已选择 `TORRENT`，直接提交即可。

## 主要功能

- 五种下载类型（四种可用 + ed2k 占位），**类型必填**，并提供按下载地址推断类型的辅助接口
- 代理节点：创建任务时按代理名称从 core 解析并快照地址/端口；`HTTP` / `MAGNET` / `TORRENT` 与 `GIT` 均消费代理
  （Git 的 HTTP(S) 克隆与 SSH 克隆都支持 HTTP / SOCKS（SOCKS5）代理；`git://` 地址不支持代理，携带代理会返回 400），
  `TORRENT` 经 URL 提交时 `.torrent` 文件由本服务拉取，走任务代理（HTTP / SOCKS 均支持，流式落盘并限制体积），
  `ED2K` 会忽略并清空代理快照
- SSH 克隆：`GIT` 类型支持 `ssh://host[:port]/path` 与 SCP 风格 `git@host:owner/repo.git`，认证方式为私钥 /
  带口令私钥 / SSH 密码，凭据按用户管理（见「SSH 克隆与凭据」）
- 实时进度（速度、已下载量、总大小）与任务状态，可中断等待中 / 运行中的任务
- 按用户分页查询任务列表，并可按状态筛选（ALL / DOWNLOADING / FINISH / FAILED）
- 落盘自动规避重名：单文件追加 `.1`、`.2`；目录树对顶层目录名同样做规避
- 多文件 / 目录树产物（BT 多文件、Git 仓库）按原目录结构递归创建目录并逐文件上传
- 下载能力声明接口：返回每种类型是否可用、不可用原因、承载引擎标识（`engineId`）以及可选项元数据
- Git 参数与 aria2 运行参数（开关 / 进度轮询间隔 / 无进度超时 / 种子体积上限）由 sfc-core 统一配置管理
  （后台修改后最长 5 秒生效，无需重启本服务）；aria2 的 RPC 地址、令牌与共享目录仍为**部署期配置**
  （本服务 `application.yml` / 环境变量，修改后需重启），支持 aria2 同机或跨容器共享目录部署
- 后台「插件配置 → 离线下载」提供「**SSH 凭据管理**」菜单：声明为 `inputType=template` 的组件
  `download-ssh-credential-manager`（由前端实现），用于管理当前登录用户的 SSH 凭据与（仅管理员）主机信任记录
- 前端插件资源（`index.umd.js` / `style.css`）随服务发布，仍由 `sfc-core` 的 `plugin/autoLoad.js` 合并加载

## 配置

配置分两类：Git 参数与 aria2 运行参数由 sfc-core 统一配置（后台「插件配置」面板，改完最长 5 秒生效）；
aria2 的 RPC 地址、令牌与共享目录是部署期配置（`application.yml` / 环境变量，修改后需重启）。

### 部署配置：aria2 连接与共享目录（改后需重启）

以下参数在**本服务的 `application.yml`**（`sfc.download.aria2.*`）中，可用环境变量覆盖；
修改后需**重启本服务**才生效。

| 配置项 | 环境变量 | 默认值 | 说明 |
|---|---|---|---|
| `sfc.download.aria2.rpc-url` | `SFC_DOWNLOAD_ARIA2_RPC_URL` | `http://127.0.0.1:6800/jsonrpc` | aria2 JSON-RPC 地址；跨容器部署填容器网络地址（如 `http://aria2:6800/jsonrpc`） |
| `sfc.download.aria2.secret` | `SFC_DOWNLOAD_ARIA2_SECRET` | 空 | 与 `aria2c --rpc-secret` 一致；只从部署配置读取，日志与接口不回显，建议用环境变量 / docker secrets，禁止提交明文 |
| `sfc.download.aria2.shared-dir` | `SFC_DOWNLOAD_ARIA2_SHARED_DIR` | 空（不启用） | 下载服务容器内共享根目录（已挂载的绝对路径）；仅 MAGNET / TORRENT 使用，留空时行为与改造前一致 |
| `sfc.download.aria2.remote-dir` | `SFC_DOWNLOAD_ARIA2_REMOTE_DIR` | 空（= `shared-dir`） | aria2 容器内同一共享根目录的 POSIX 绝对路径；留空表示两容器挂载点路径一致 |

> 旧的部署配置 `sfc.download.aria2.enabled` / `poll-interval` / `stall-timeout` / `torrent-max-size`（及对应
> `SFC_DOWNLOAD_ARIA2_*` 环境变量）已**不再读取**；这 4 项已迁移到 core 统一配置（见下节）。

### core 统一配置：Git 与 aria2 运行参数（改后最长 5 秒生效）

运行期参数存放在 `sfc-core` 的系统配置表：

- 管理员在 core 后台的「插件配置」面板（节点名「离线下载」→「Git 克隆」/「aria2 P2P 下载」）修改；
- 本服务通过 `SfcCoreConfigFeignClient` 读取，快照缓存 5 秒，**修改后最长 5 秒生效，无需重启**；
- 配置面板还保留「SSH 凭据管理」template 菜单（不是配置项，不占用配置 key），其组件契约与接口说明见
  `microservices/sfc-download-service/docs/git-proxy-ssh-clone-frontend-guide.md`。

Git 克隆参数：

| 配置 key | 默认值 | 说明 |
|---|---|---|
| `download.git.default-depth` | `0` | 默认克隆深度，`0` 表示全量克隆 |
| `download.git.keep-git-dir` | `false` | 是否保留 `.git`；默认删除（网盘场景只需要工作区文件） |
| `download.git.connect-timeout` | `30` | JGit 连接超时（秒） |

aria2 运行参数：

| 配置 key | 默认值 | 说明 |
|---|---|---|
| `download.aria2.enabled` | `false` | P2P 总开关；关闭时磁力链与种子在创建任务阶段即被 400 拒绝 |
| `download.aria2.poll-interval` | `1000` | 进度轮询间隔（毫秒） |
| `download.aria2.stall-timeout` | `300` | 无进度超时（秒）：连续该时长已下载字节无增长即判定失败；`0` 表示不限（永久等待，任务会一直占用下载并发位，需手动中断） |
| `download.aria2.torrent-max-size` | `2` | Base64 种子内容上限（MB） |

SSH 克隆与 SSH 凭据管理**没有新增统一配置项**：凭据、主机信任记录都存放在本服务 `download.db`，
Git 的克隆行为仍由 `download.git.*` 控制。

`application.yml` 保留部署期 / 内部调优参数：端口、数据源、`sfc.internal.key`、Worker 并发、注册中心，
`sfc.download.config.refresh-interval-millis`（统一配置快照缓存，默认 5000）、
`sfc.download.capability.cache-seconds`（能力探测缓存，默认 30）以及 aria2 的连接 / 共享目录参数
`sfc.download.aria2.{rpc-url,secret,shared-dir,remote-dir}`。

**降级行为**：core 不可用时，Git 与 aria2 运行参数读取回落为配置实体上的注解默认值（Git：全量克隆、
删除 `.git`；aria2：`enabled=false`、轮询 1000 毫秒、无进度超时 300 秒、种子上限 2 MB），并只打一条 WARN；
不会阻塞服务启动，也不影响 HTTP / Git 下载。aria2 的 RPC 地址 / 令牌 / 共享目录仍来自本服务部署配置。

## aria2 部署要求

磁力链与种子依赖外部 aria2 进程（JSON-RPC），支持两种部署方式：

1. **同机部署**（最简单）：aria2 与下载服务运行在同一台机器 / 同一文件系统上，**不配置共享目录**，
   行为与改造前一致。`rpc-url` 填本机地址（默认 `http://127.0.0.1:6800/jsonrpc`）。
2. **跨容器 / 跨机部署（共享卷 + 双路径映射）**：两个容器挂载**同一份共享存储**，并在两侧分别配置路径：
   - `shared-dir`：下载服务容器内共享根目录（已挂载）的绝对路径，任务目录为
     `<shared-dir>/offline-download/<任务id>-<UUID>`；
   - `remote-dir`：aria2 容器内同一共享根目录的 POSIX 绝对路径；留空表示两容器挂载点路径一致；
   - `rpc-url` 填从下载服务可达的 aria2 地址（跨容器用容器网络地址，如 `http://aria2:6800/jsonrpc`）。

   映射示例（宿主机 `/host/shared`，两容器路径不同时必须显式配置 `remote-dir`）：

   ```text
   下载服务:  -v /host/shared:/shared/download   shared-dir=/shared/download
   aria2:     -v /host/shared:/downloads        remote-dir=/downloads
   任务目录在 aria2 侧: /downloads/offline-download/<任务id>-<UUID>
   ```

   共享目录只用于 MAGNET / TORRENT（HTTP / GIT 仍用本服务 JVM 临时目录）。未配置 `shared-dir` 时要求
   aria2 与下载服务同机 / 同文件系统；配置后漏挂共享卷时，能力探测会报共享目录不存在（不会自动创建本地根目录）。
3. **权限对齐**：下载服务镜像以 root 运行；aria2 容器需使用可读写共享卷的用户（compose 示例为
   `PUID=0` / `PGID=0`，`UMASK` 建议 `022`），否则会出现产物读取 / 清理失败。
4. 启动示例（令牌需与 `sfc.download.aria2.secret` 一致）：

   ```shell
   # 同机部署（RPC 仅本机可达）
   aria2c --enable-rpc --rpc-listen-all=false --rpc-listen-port=6800 \
          --rpc-secret=<与配置一致的令牌> --dir=<任意可写目录>

   # 跨容器 / 跨机部署（RPC 需容器网络可达，仅暴露在内网）
   aria2c --enable-rpc --rpc-listen-all=true --rpc-listen-port=6800 \
          --rpc-secret=<与配置一致的令牌> --dir=<共享卷内的兜底目录>
   ```

   命令行里的 `--dir` 只是兜底：**每次任务的下载目录由服务端通过 RPC 选项强制指定**（配置共享目录时为映射后的
   aria2 侧路径）。
5. 服务端会强制覆盖以下选项，用户传入同名项会被丢弃：`dir`、`seed-time=0`（下载完成即停止做种）、
   `bt-save-metadata=false`、`follow-torrent=false`。
6. **配置生效方式**：aria2 运行参数（开关 / 轮询间隔 / 无进度超时 / 种子体积上限）在 core 后台面板维护，
   改完最长 5 秒生效；`rpc-url` / `secret` / `shared-dir` / `remote-dir` 是部署期配置，修改后需重启本服务。
   `GET /capabilities?refresh=true` 会强制刷新统一配置快照，但 aria2 连通性探测缓存 30 秒不变。

用户可通过任务的 `options.aria2Options` 透传选项，取值为 JSON 对象文本（键为 aria2 选项名、值统一按字符串），
例如 `{"split":"16","max-connection-per-server":"16"}`；服务端仅放行白名单：
`split`、`max-connection-per-server`、`min-split-size`、`bt-tracker`、`max-download-limit`、
`max-overall-download-limit`、`continue`、`user-agent`、`referer`。

## SSH 克隆与凭据

Git 类型除 `http` / `https` / `git://` 外还支持 SSH 克隆，依赖 `org.eclipse.jgit.ssh.apache`（Apache MINA sshd，
纯 Java 实现），部署机**无需**安装 git / ssh 客户端。

| 项 | 说明 |
|---|---|
| 放行来源 | `ssh://host[:port]/path`、SCP 风格 `git@host:owner/repo.git` |
| 继续拒绝 | `file://`、`ext::`、以 `-` 开头的地址、本地绝对路径 |
| 认证方式 | 私钥、带口令（加密）私钥、SSH 密码 |
| 代理 | SSH 与 HTTP(S) 克隆都支持 HTTP 代理与 SOCKS（SOCKS5）代理；`git://` 地址不支持代理 |
| 宿主机 `~/.ssh` | **不读取**（密钥、`known_hosts`、`config` 均不读取），SSH 认证完全由用户凭据驱动 |

实现上按任务注入、不改 JVM 全局代理：HTTP(S) 克隆用 JGit `TransportHttp` 注入 `HttpConnectionFactory`；
SSH 克隆通过 `SshdSessionFactory` 的 `ProxyDataFactory` 生效（HTTP CONNECT / SOCKS，即 SOCKS5）。

### 用户级 SSH 凭据

凭据存放在本服务 SQLite 新表 `ssh_credential`，按用户（`uid`）隔离，只有本人可以查看、使用与删除：

- 字段：`name`、`authType`（`PRIVATE_KEY` / `PASSWORD`）、`username`、`privateKey`、`passphrase`、`password`、
  `fingerprint`（私钥公钥指纹 `SHA256:...`）以及审计字段 `uid` / `createAt` / `updateAt`；
- 私钥 / 口令 / 密码**明文存放于本服务 `download.db`**（与第三方平台凭证做法一致），
  接口**绝不回显**，日志打码；建议限制 `download.db` 的文件权限；
- 保存时校验：私钥必须可解析（加密私钥用口令解密）并自动计算指纹；`PASSWORD` 方式必须提供 `username` + `password`；
  校验失败返回 400。

创建 `GIT` + SSH 地址的任务时，用**顶层字段** `sshCredentialId` 指定凭据（不是 `options` 里的键）：

- SSH 地址必须提供，否则 400「SSH 地址必须选择 SSH 凭据（sshCredentialId）」；
- 非 SSH 地址携带该字段 → 400「仅 SSH 地址支持选择 SSH 凭据」；
- 服务端校验凭据存在且属于当前创建者，否则 400「无效的 SSH 凭据: {id}」；任务参数只保存凭据 id，不保存密钥内容；
- 私钥认证时，SSH 地址未携带用户名（如 `ssh://host/owner/repo.git`）则必须在凭据中配置 `username`，
  否则 400「SSH 地址未携带用户名，请在 SSH 凭据中配置用户名」；SCP 风格地址自带用户名，无需额外配置；
- 执行时凭据已被删除 → 任务失败「SSH 凭据不存在或已被删除: {id}」。

**子模块：** 开启 `gitSubmodules` 时，任务级代理与 SSH 凭据会传递给子模块；子模块地址同样受传输协议
白名单约束（仅允许 `http` / `https` / `ssh` / `git`），`file://`、`ext::` 与本地路径会被拒绝，
防止恶意仓库通过 `.gitmodules` 让服务端读取本地仓库或执行外部命令。

### 主机密钥信任记录（TOFU）

首次连接未知主机时自动接受，并把主机密钥写入本服务新表 `ssh_host_key`（字段 `connectAddress`、`keyType`、
`publicKey`、`fingerprint`、首次信任用户 `uid`、`createAt`），`connectAddress` 形如 `github.com` 或
`github.com:2222`。信任记录**全局共享**（不按用户隔离）；之后同一主机指纹发生变化时克隆失败并给出可读提示，
管理员在信任记录中删除旧记录后重试即可。首次信任的记录**写入失败时拒绝本次连接**（fail-closed），
避免「信任未记录却放行」导致每次连接都重新首次信任、TOFU 失效。

### 接口

| 方法 | 内部路径 | 权限 | 说明 |
|---|---|---|---|
| GET | `/api/task/download/ssh-credential` | 登录用户 | 当前用户 SSH 凭据列表（不含私钥/口令/密码；返回 `id/name/authType/username/fingerprint/createAt`） |
| POST | `/api/task/download/ssh-credential` | 登录用户 | 新增凭据 |
| DELETE | `/api/task/download/ssh-credential?id=` | 登录用户 | 删除本人凭据 |
| GET | `/api/task/download/ssh-host-key?connectAddress=` | 管理员 | 信任记录列表（`connectAddress` 可选过滤） |
| DELETE | `/api/task/download/ssh-host-key?id=` | 管理员 | 删除信任记录 |

新增凭据请求体：

```jsonc
{
  "name": "GitHub 部署密钥",
  "authType": "PRIVATE_KEY",     // PRIVATE_KEY | PASSWORD
  "username": "git",             // 可选（PASSWORD 方式必填）
  "privateKey": "-----BEGIN OPENSSH PRIVATE KEY-----\n...\n-----END OPENSSH PRIVATE KEY-----",
  "passphrase": "可选：加密私钥口令"
}
```

密码方式：`{"name":"...","authType":"PASSWORD","username":"git","password":"..."}`。

列表项示例：`{"id":"123","name":"GitHub 部署密钥","authType":"PRIVATE_KEY","username":"git","fingerprint":"SHA256:xxxx","createAt":"..."}`；
主机信任记录示例：`{"id":"1","connectAddress":"github.com","keyType":"ssh-ed25519","publicKey":"ssh-ed25519 AAAA...","fingerprint":"SHA256:xxxx","uid":"1","createAt":"..."}`。
经网关访问时，上述内部路径前缀为 `/api/service/sfc-download-service`。凭据管理不提供编辑（更新）接口，需要修改时请删除后重新新增。

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
| GET | `/api/service/sfc-download-service/task/download/ssh-credential` | 当前用户 SSH 凭据列表（需登录；不含私钥/口令/密码） |
| POST | `/api/service/sfc-download-service/task/download/ssh-credential` | 新增 SSH 凭据（需登录；私钥 / 带口令私钥 / 密码） |
| DELETE | `/api/service/sfc-download-service/task/download/ssh-credential?id=` | 删除本人 SSH 凭据（需登录） |
| GET | `/api/service/sfc-download-service/task/download/ssh-host-key?connectAddress=` | SSH 主机信任记录列表（管理员；`connectAddress` 可选过滤） |
| DELETE | `/api/service/sfc-download-service/task/download/ssh-host-key?id=` | 删除 SSH 主机信任记录（管理员） |

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
`refresh=true` 会先强制刷新 core 统一配置（Git 与 aria2 运行参数）快照；aria2 连通性探测另有 30 秒缓存，
且其 RPC 地址 / 令牌 / 共享目录为部署期配置，修改后需重启服务，`refresh=true` 无法让其立即生效。

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
- **主机密钥为 TOFU（首次信任）**：SSH 首次连接未知主机时自动接受并写入 `ssh_host_key`，记录全局共享；
  主机指纹变化会导致克隆失败，需管理员删除旧信任记录后重试。首个用户可抢先写入错误的主机密钥造成他人
  克隆失败（属 DoS，不是中间人攻击），管理员删除记录即可恢复。
- **SSH 凭据明文存储**：私钥 / 口令 / 密码明文存放于本服务 `download.db`（与第三方平台凭证做法一致），
  接口不回显、日志打码；建议限制 `download.db` 文件权限。本服务**不读取**宿主机 `~/.ssh`，认证完全由用户凭据驱动。
- **Git 子模块边界**：任务级的代理与 SSH 凭据会传递给子模块；子模块地址仅允许 `http`/`https`/`ssh`/`git`
  （`file://`、`ext::`、本地路径会被拒绝）；**HTTP(S) 父仓库 + SSH 子模块**不在支持范围。
  开启子模块克隆且 `stripGitDir=true` 时，子模块内的 `.git`（目录或 gitlink 文件）也会一并清理。
- **多实例部署**：本服务 SQLite 不共享，SSH 凭据与主机信任记录按实例隔离（与下载任务数据一致的既有架构限制）。
- **Git 进度是近似值**：JGit 以「工作单元」而非字节汇报进度，进度条按工作单元累加，不能当作字节进度使用。
- **aria2 内容下载不支持 SOCKS 代理**：SOCKS 会被 aria2 忽略并记录日志，HTTP 代理会转为 `all-proxy`
  传给 aria2；`TORRENT` 任务经 URL 提交时，`.torrent` 文件由本服务按任务代理快照拉取（HTTP / SOCKS 均支持）。
  跨容器部署需挂载共享目录并配置 `shared-dir` / `remote-dir`（见「aria2 部署要求」）；未配置 `shared-dir` 时
  要求 aria2 与下载服务同机 / 同文件系统。
- **aria2 secret 管理**：`sfc.download.aria2.secret` 只从部署配置（环境变量 / docker secrets）读取，日志与接口
  不回显；P2P 开关 / 轮询 / 超时 / 种子体积上限则在 core 后台统一配置（与本项不同源）。环境变量在
  `docker inspect`、compose 文件与 shell 历史中可能可见，生产环境应使用 docker secrets 或受控的部署配置，
  禁止把明文提交到仓库。
- **任务不重试 / 不续传**：执行失败即失败（是否重新排队由异步任务框架的故障转移策略决定，重跑会从头下载）。
- **落盘失败不回滚**：目录树落盘过程中若某个文件上传失败，已上传成功的文件不会删除，失败信息会提示已成功数量。
  空目录不会保留（上传只处理文件）。
- **单文件大小受限**：仍受 core 的 `spring.servlet.multipart.max-file-size`（默认 32GiB）约束，超限会以
  `413 Content Too Large` 失败，需要调大 core 该配置。
- **统一配置降级**：core 不可用时 Git 与 aria2 运行参数均回落为注解默认值（Git：全量克隆、删除 `.git`；
  aria2：`enabled=false`、轮询 1000 毫秒、无进度超时 300 秒、种子上限 2 MB），只 WARN 一次；HTTP 与 Git
  下载本身不受影响。aria2 的 RPC 地址 / 令牌 / 共享目录来自本服务部署配置；若 core 从未成功返回过配置，
  兜底的 `enabled=false` 会让 P2P 在 core 恢复前保持不可用。
- **代理契约**：旧版前端提交的是代理「名称」，服务端按名称解析并快照地址/端口；之后修改/删除 core 的代理配置
  不影响已创建任务。
- **历史数据不迁移**：插件期下载记录在宿主 `xyy.db`，微服务使用独立 `download.db`，不会自动出现。
- 本服务信任网关注入的 `X-Sfc-Uid`，只允许内网 / 本机暴露，对外入口只有网关。
