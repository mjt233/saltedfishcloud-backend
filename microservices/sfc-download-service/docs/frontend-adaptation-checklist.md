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
  - `POST`：`{type, url, uid, savePath, options?, torrentBase64?, headers?, method?, proxy?, connectTimeout?, readTimeout?}`
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
`proxy` 仅对 `HTTP` / `MAGNET` / `TORRENT` 生效，`GIT` 会忽略并清空。

### 6.3 列表响应的增量字段

| 字段 | 说明 |
|---|---|
| `type` | 下载类型编码（创建时提交的值）；升级前创建的历史记录该列为空，返回 `null` |

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
