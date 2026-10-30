# 前端适配清单（sfc-ext-download 插件 → sfc-download-service 微服务）

> 面向：调用离线下载接口的前端工程。
> 背景：后端 `sfc-ext-download` 已从宿主插件改造为独立微服务 `microservices/sfc-download-service`，
> 按约定**不新增网关显式路由**，前端使用网关默认动态路由访问；前端 URL 由前端侧手动调整。

---

## 一、必须改：API 基址

旧地址（插件期，core 直接提供）：

```
/api/task/download
/api/task/download/proxy
```

新地址（网关默认动态路由：`/api/service/{serviceId}/**` 剥离前缀并补 `/api` 后转发）：

```
/api/service/sfc-download-service/task/download
/api/service/sfc-download-service/task/download/proxy
```

- 前端原来把 `/task/download` 作为接口前缀的地方，改为 `/service/sfc-download-service/task/download`
  （若前端 SDK 会自动补 `/api`，则只需把插件前缀改为 `/service/sfc-download-service/task/download`）。
- 过渡期：未修改前，旧地址 `/api/task/download/**` 会落到 core 的兜底路由并返回 **404**（core 已不再加载该插件），属预期。
- 请求方法、参数不变；请求体新增**必填**的 `type`：
  - `POST`：`{type, url, uid, savePath, options?, torrentBase64?, headers?, method?, proxy?, sshCredentialId?, connectTimeout?, readTimeout?}`
    （`type` 缺失会返回 HTTP 422，服务端不再自动识别；自动选型请调用 `GET /task/download/detect-type?url=`）
  - `GET`：`uid, page, size, type(ALL/DOWNLOADING/FINISH/FAILED)`
  - `DELETE`：`taskId`（多余的 `uid` 参数会被忽略）
  - `GET /proxy`：代理节点列表（本前端此前调用的端点当时后端不存在，现在已补齐）

## 二、不用改：响应结构与字段

| 项 | 说明 |
|---|---|
| 响应信封 | 仍为 `{code, businessCode, msg, data}`。业务异常与宿主一致：HTTP 状态可能仍为 200，业务结果以信封 `code` 为准（与 core 现有接口行为一致） |
| 分页结构 | ⚠️ **字段有变化**：旧插件直接返回 Spring `Page` JSON（`content` / `totalElements` / `totalPages` / …）；新版统一返回 `SfcPageInfo`（`content` / `totalCount` / `totalPage`）。`content` 字段名不变，若前端只用 `data.content` 则无需改动；若用了 `totalElements`/`totalPages`，请改为 `totalCount`/`totalPage` |
| 列表项字段 | `id / uid / url / proxy / message / loaded / size / speed / name / savePath / createdAt / finishAt / taskId / asyncTaskRecord{id,status}` 均保留 |
| 状态语义 | `asyncTaskRecord.status`：0 等待、1 运行、2 完成、3 失败、4 取消、5 离线（与 core 一致） |
| 任务状态筛选 | `type` 取值与含义不变 |

⚠️ 与宿主统一的序列化约定：**ID 字段与数值字段在 JSON 中为字符串**
（`id`、`taskId`、`asyncTaskRecord.id`、`size`、`loaded`、`speed`、`totalCount`、`totalPage` 等），
`asyncTaskRecord.status` 为数字。这与 core 现有 `JacksonConfig` 的行为一致，前端原有数值兼容逻辑可继续工作；
但请勿用 `=== 0` 这类严格比较判断字符串数值。

## 三、前端资源加载（无需改）

- `index.umd.js` / `style.css` 随本服务发布，仍然通过 core 的 `GET /api/plugin/autoLoad.js` 合并加载
  （core 按注册中心 metadata `sfc.plugin.autoload` 从服务 `/static/**` 拉取），主前端加载链路不变。
- 本服务**不再提供** `plugin-info.json` / 配置面板：离线下载没有插件配置项，管理员后台不会出现对应配置节点。
- 前端构建方面（前端仓库侧人工处理）：旧构建脚本把产物复制到 `sfc-ext/sfc-ext-download/src/main/assert/static/`，
  该目录已随插件删除；需改为输出到 `microservices/sfc-download-service/src/main/resources/static/`。

## 四、数据与部署

1. **历史数据不迁移**：下载任务改存本服务独立 SQLite `download.db`；`xyy.db` 中的历史下载记录不会自动出现，
   需保留请人工导出/导入。
2. **部署必须删除旧插件 jar**：运行目录 `ext/sfc-ext-download.jar`（及 `release/ext-available/` 同名产物）不删除会导致
   core 继续加载旧插件（重复任务执行者 + 前端资源重复加载）。
3. `SFC_INTERNAL_KEY`：core 与 sfc-download-service 必须配置同一份（≥32 字符），否则任务提交/进度校准/中断不可用。
4. 离线下载服务只允许内网/本机暴露，对外入口只有网关；前端不要直连 8092。

## 五、联调前置

| 依赖 | 要求 |
|---|---|
| `sfc-core` | 已启动并注册（local registry 或 Nacos，group=SFC/namespace 空串） |
| `sfc-download-service` | 已启动（8092）并注册；Worker 日志显示 `offline-download` 类型已声明 |
| `sfc-gateway` | 已启动；默认动态路由生效即可（无需为本服务加显式路由） |
| 登录态 | 创建/列表/中断需要登录用户；`uid=0`（公共网盘）写入仅管理员 |

---

## 六、多类型下载增量变更（HTTP / GIT / MAGNET / TORRENT）

> 本次改造为本服务新增了 Git 克隆、磁力链、种子（aria2）三种下载方式，并登记了 ed2k 类型（引擎未接入）。
> **完整的字段表、示例 JSON、错误文案与联调步骤见
> [`frontend-adapter-guide.md`](./frontend-adapter-guide.md)**；本节只列出相对上一版 checklist 的增量。

### 6.1 新增接口

| 方法 | 网关路径 | 说明 |
|---|---|---|
| GET | `/api/service/sfc-download-service/task/download/capabilities` | 查询支持的下载类型与可用性（未登录时信封 `code=401`，**HTTP 仍为 200**）。可选参数 `refresh=true` 强制刷新配置快照。每项含 `type/name/engineId/available/reason/options` |
| GET | `/api/service/sfc-download-service/task/download/detect-type?url=` | 按下载地址推断类型（需登录）；成功时 `data` 为类型编码字符串（如 `"GIT"`），无法识别时 `data=null`（仍为 `code=200`） |

### 6.2 创建任务请求体的增量字段

| 字段 | 说明 |
|---|---|
| `type` | **必填**：`HTTP` / `GIT` / `MAGNET` / `TORRENT` / `ED2K`；缺失 → HTTP 422，`AUTO`/未知值 → HTTP 400。不再支持省略后由服务端自动识别，自动选型请调用 `detect-type` |
| `options` | 类型专属参数：Git 的 `gitBranch`/`gitDepth`/`gitSubmodules`/`stripGitDir`/`gitUsername`/`gitToken`；P2P 的 `aria2Options`（JSON 对象文本，如 `"{\"split\":\"16\"}"`） |
| `torrentBase64` | 种子文件内容的 Base64，与 `url` 二选一（此时 `type` 必须为 `TORRENT`） |

同时行为变化：`url` 不再限定为 HTTP(S) 链接（磁力链、ed2k、Git 地址均可），格式校验改为按 `type` 进行。
`proxy` 对 `HTTP` / `MAGNET` / `TORRENT` 以及 `GIT`（HTTP(S) 与 SSH 克隆）生效；`git://` 地址不支持代理
（带代理创建会返回 400 业务错误），`ED2K` 仍忽略并清空代理。

### 6.3 列表响应的增量字段

| 字段 | 说明 |
|---|---|
| `type` | 下载类型编码（创建时提交的值）；升级前创建的历史记录该列为空，返回 `null` |
| `name` | 任务展示名称：HTTP 运行中为探测到的文件名、完成后为保存到网盘的文件名；GIT 为保存的顶层目录名；MAGNET/TORRENT 为下载文件名摘要（1 个文件即文件名，2~3 个以 `、` 连接，超过 3 个为前 3 个 + `等...`），任务运行中即可有值。BT 多文件任务该字段仅用于展示，不能拼成网盘路径（真实顶层目录名是种子信息名） |

其余字段、分页结构（`content` / `totalCount` / `totalPage`）、数值字符串化约定均不变。

### 6.4 错误码的增量变化

> ⚠️ **业务结果以信封 `code` 为准，HTTP 状态码不可靠。** 业务异常（未登录、任务不存在、引擎未启用、链接非法等）
> 返回的是 **HTTP 200 + 信封 `code`**，与宿主 `sfc-core` 的既有行为一致。**不要写 `if (response.status === 400)`**，
> 请判断 `body.code === 200`，失败时展示 `body.msg`。

| 场景 | HTTP 状态 | 信封 `code` |
|---|---|---|
| 未登录 | 200 | 401 |
| 任务不存在 | 200 | 404 |
| 引擎未启用 / 链接非法 / 类型不支持 | 200 | 400 |
| 请求体无法反序列化（如 `"type":"FOO"` 或 `"type":"AUTO"`） | **400** | 400 |
| 查询参数类型不匹配（如 `?type=FOO`） | **400** | 400 |
| Bean Validation 失败（缺 `type`、`savePath` 为空、`size` 越界等） | **422** | 422 |
| 资源权限不足（非创建者中断任务） | **403** | 403 |

其余增量：

- 新增大量 **400** 业务错误（非法类型、非法 Git 协议、磁力链格式错、引擎不可用等），文案可直接展示；
- 非法枚举值（如 `type=FOO`）现在稳定返回 **400**（此前会落到 500 兜底）。

### 6.5 前端资源

本次**没有修改** `src/main/resources/static/index.umd.js` 与 `style.css`，仍是旧版本。
前端需在前端仓库完成改造后重新构建，并把产物输出到本服务的 `src/main/resources/static/`。

---

## 七、SSH 克隆与凭据增量变更（GIT 类型）

> 本次在 Git 类型上新增 SSH 克隆（`ssh://host[:port]/path` 与 SCP 风格 `git@host:owner/repo.git`），
> 并新增用户级 SSH 凭据管理与管理员侧主机信任记录管理。完整的字段表、示例 JSON 与前端交互建议见
> [`frontend-adapter-guide.md`](./frontend-adapter-guide.md) 的 §五 / §九 / §十；**本次改造的专项对接说明**
> （含页面交互、配置面板 template 组件、联调用例）见
> [`git-proxy-ssh-clone-frontend-guide.md`](./git-proxy-ssh-clone-frontend-guide.md)。本节只列出增量。

### 7.1 类型与代理的能力变化

- `GIT` 地址现支持 `http(s)` / `git://` / `ssh://host[:port]/path` / `git@host:owner/repo.git`；
- `proxy` 对 `GIT` 生效：HTTP(S) 克隆与 SSH 克隆都支持 HTTP 代理与 SOCKS（SOCKS5）代理；`git://` 地址带代理会返回 400；
- `capabilities` 与 `detect-type` 的规则**不变**：SSH 地址仍推断为 `GIT`，`GIT` 的 `options` 没有新增键
  （SSH 凭据是任务的**顶层字段**，不在 `options` 里）。

### 7.2 新增接口

| 方法 | 网关路径 | 权限 | 说明 |
|---|---|---|---|
| GET | `/api/service/sfc-download-service/task/download/ssh-credential` | 登录用户 | 当前用户 SSH 凭据列表（不含私钥/口令/密码；返回 `id/name/authType/username/fingerprint/createAt`） |
| POST | `/api/service/sfc-download-service/task/download/ssh-credential` | 登录用户 | 新增凭据（返回新增后的凭据元信息） |
| DELETE | `/api/service/sfc-download-service/task/download/ssh-credential?id=` | 登录用户 | 删除本人凭据（不存在或非本人返回 400） |
| GET | `/api/service/sfc-download-service/task/download/ssh-host-key?connectAddress=` | 管理员 | 主机信任记录列表（`connectAddress` 可选过滤；非管理员信封 `code=403`，HTTP 仍为 200） |
| DELETE | `/api/service/sfc-download-service/task/download/ssh-host-key?id=` | 管理员 | 删除信任记录（记录不存在返回 404） |

### 7.3 创建任务新增顶层字段

| 字段 | 说明 |
|---|---|
| `sshCredentialId` | 仅 `type=GIT` 且地址为 SSH 时使用：**SSH 地址必填**，否则 400「SSH 地址必须选择 SSH 凭据（sshCredentialId）」；非 SSH 地址携带该字段 → 400「仅 SSH 地址支持选择 SSH 凭据」；凭据必须存在且属于当前创建者（否则 400「无效的 SSH 凭据: {id}」）；私钥认证时 SSH 地址未携带用户名且凭据未配置 `username` → 400「SSH 地址未携带用户名，请在 SSH 凭据中配置用户名」。JSON 中可为数字或字符串，本服务 Long 统一序列化为**字符串**；执行前凭据被删除 → 任务失败「SSH 凭据不存在或已被删除: {id}」 |

### 7.4 错误文案补充

> 仍以信封 `code` 判定结果；未登录、凭据非法、非管理员等业务失败时 HTTP 状态**可能仍是 200**。

| 场景 | 信封 `code` | 典型 `msg` |
|---|---|---|
| 凭据接口未登录 | 401 | `请先登录` |
| 非管理员访问主机信任记录接口 | 403 | `仅管理员可管理 SSH 主机信任记录` |
| SSH 地址未提供凭据 | 400 | `SSH 地址必须选择 SSH 凭据（sshCredentialId）` |
| 非 SSH 地址携带 `sshCredentialId` | 400 | `仅 SSH 地址支持选择 SSH 凭据` |
| 非 `GIT` 类型携带 `sshCredentialId` | 400 | `仅 Git SSH 克隆支持选择 SSH 凭据` |
| 凭据不存在或不属于当前用户 | 400 | `无效的 SSH 凭据: {id}` |
| 私钥认证且 SSH 地址未携带用户名、凭据也未配置用户名 | 400 | `SSH 地址未携带用户名，请在 SSH 凭据中配置用户名` |
| `git://` 地址带代理 | 400 | `git:// 协议不支持代理，请改用 http(s) 或 ssh 地址` |
| 删除不存在的信任记录 | 404 | `SSH 主机信任记录不存在: {id}` |
| 私钥无法解析 / 密码方式缺用户名 | 400 | `SSH 私钥解析失败: <原因>` / `SSH 密码认证需要提供用户名` |
| SSH Git 地址格式非法 | 400 | `无效的 SSH Git 仓库地址: <原文>` |
| 新增凭据缺少 `name` / `authType` | 422 | `name: SSH 凭据名称不能为空` / `authType: SSH 认证方式不能为空` |

### 7.5 前端交互建议

- 「下载类型」为 `GIT` 且地址以 `ssh://` 开头、或形如 `user@host:path` 时，展示「SSH 凭据」下拉框
  （调用 `GET /ssh-credential`），选中后把凭据 id 放进请求体**顶层** `sshCredentialId`；
- 非 SSH 地址时隐藏该字段且**不要提交**（否则返回 400）；地址切换为 SSH 但用户未选凭据时，提交前先提示选择；
- 无可用凭据时提供「新增凭据」入口（私钥 / 带口令私钥 / 密码三种输入），新增成功后刷新下拉并选中新凭据；
- 凭据列表不含私钥 / 口令 / 密码，仅能用 `name` / `authType` / `username` / `fingerprint` / `createAt` 展示；
  **没有编辑（更新）接口**，修改请删除后重新新增；
- 管理员端可提供主机信任记录列表（按 `connectAddress` 过滤）与删除操作；普通用户调用会得到信封 `code=403`。

### 7.6 后台配置面板新增 template 组件

`config-properties.json` 新增菜单「SSH 凭据管理」，其配置项为 `inputType=template`：

- 组件名：`download-ssh-credential-manager`（前端按现有 template 组件规范注册）；
- `params` 提供 `credentialApi` / `hostKeyApi` / `authTypes` / `sections`（我的凭据 + 主机信任记录）/
  `supportsUpdate=false` / `hostKeyAdminOnly=true` 等契约字段；
- 组件建议复用 §7.5 的凭据管理与主机信任记录逻辑；非管理员调用主机信任记录接口返回信封 `code=403`。

组件契约与完整实现要求见
[`git-proxy-ssh-clone-frontend-guide.md`](./git-proxy-ssh-clone-frontend-guide.md) 的 §2.4。
