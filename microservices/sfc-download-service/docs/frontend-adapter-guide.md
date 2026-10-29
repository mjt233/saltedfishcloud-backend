# 离线下载 · 多类型下载前端对接指南

> 适用版本：`sfc-download-service` 多类型下载改造（HTTP / GIT / MAGNET / TORRENT 可用，ED2K 已登记但未接入引擎）；
> GIT 已支持 SSH 克隆（用户级 SSH 凭据 + 主机密钥信任记录），凭据/信任记录管理接口见 §九。
> **Git 代理 + SSH 克隆专项对接说明**（页面交互、配置面板 template 组件、联调用例）见
> [`git-proxy-ssh-clone-frontend-guide.md`](./git-proxy-ssh-clone-frontend-guide.md)。
> 本文档依据服务端代码静态整理，**未经端到端联调验证**；如与实测行为不一致，以实测为准并回馈后端。
> 接口基址、鉴权、响应信封等基础约定见 [`frontend-adaptation-checklist.md`](./frontend-adaptation-checklist.md)。

---

## 一、本次前端需要改的地方（先看这一节）

| # | 改动 | 是否必须 |
|---|---|---|
| 1 | 创建任务时**必须显式提交 `type`**（服务端不再自动识别，缺失返回 422） | 必须 |
| 2 | 用户在创建页修改/粘贴 URL 并停止输入后，调用 `GET /detect-type?url=` 自动选中「下载类型」（用户仍可手动改），见 §4.5 | 必须（否则用户要自己选类型） |
| 3 | 创建页首屏调用 `GET /capabilities`，用它渲染「下载类型」下拉框、不可用类型的灰态 + 原因提示与各类型的 `options` 子字段 | 必须 |
| 4 | 种子下载支持「上传 `.torrent` 文件 → Base64 → 放入 `torrentBase64`」（此时 `type` 填 `TORRENT`） | 需要做种子功能时 |
| 5 | 任务列表增加类型标签/图标（读取列表项的 `type`；历史记录该字段可能为 `null`） | 建议 |
| 6 | 错误提示改为**按信封 `code` 判定成功/失败**并展示 `msg`（业务失败时 HTTP 常仍为 200） | 必须 |
| 7 | 前端仓库重新构建，产物输出到本服务 `src/main/resources/static/`（本服务**没有**内置可用的新前端产物） | 必须 |
| 8 | 用 SSH 地址做 Git 克隆时，先让用户选择**用户 SSH 凭据**，把凭据 id 放进请求体顶层 `sshCredentialId`（不是 `options`）；无凭据时引导新增（§九） | 用到 SSH 克隆时 |
| 9 | 管理员端可增加「SSH 主机信任记录」管理（列表 + 删除），普通用户不可见（§九） | 建议 |
| 10 | 实现后台配置面板 template 组件 `download-ssh-credential-manager`（「我的 SSH 凭据」+「SSH 主机信任记录」两个页签） | 使用后台管理面板时 |

> ⚠️ **不再兼容「只传 `url` / `uid` / `savePath`」的旧请求体**：`type` 现为必填，缺失会被 Bean Validation 拒绝（HTTP 422 + 信封 `code=422`）。自动选型请改用 §4.5 的 `detect-type` 接口，由前端把结果放进请求体。

---

## 二、接口基址

| 访问方式 | 基址 | 说明 |
|---|---|---|
| 经网关（生产/前端默认） | `/api/service/sfc-download-service/task/download` | 网关动态路由，剥离 `/api/service/{serviceId}` 后补 `/api` 转发到本服务 |
| 直连服务（仅联调） | `http://<host>:8092/api/task/download` | 需要自行构造 `X-Sfc-Uid` 信任头；**前端不要直连** |

服务内部路径前缀为 `/api/task/download`，下列接口路径均相对于该前缀。

---

## 三、通用约定

### 3.1 响应信封

所有接口返回统一信封：

```json
{ "code": 200, "businessCode": 200, "msg": null, "data": {} }
```

- **业务结果只看信封 `code`，不要看 HTTP 状态码**。未登录、任务不存在、引擎未启用、链接非法等业务失败时，HTTP 状态通常仍是 **200**，错误码只体现在 `code` 里，`msg` 为可直接展示的中文提示；
- 只有「请求体无法解析 / 参数类型不匹配」（HTTP 400）、「Bean Validation 失败」（HTTP 422）与
  「携带 `SfcErrorInfo` 的资源级权限异常」（HTTP 403，如非创建者中断任务）会返回非 200，详见 §10.1；
  其它权限问题（如非管理员访问 SSH 主机信任记录接口）只体现在信封 `code=403`，HTTP 仍为 200；
- 业务失败（如下载执行失败）**不会**体现在创建接口上，而是记录在任务的 `status` 与 `message` 字段。

### 3.2 数值字段是字符串

本服务与宿主 `sfc-core` 保持一致：`long` / `Long` 一律序列化为**字符串**，避免 JS 64 位精度丢失。

| 字段 | JSON 类型 |
|---|---|
| `id`、`taskId`、`size`、`loaded`、`speed`、`totalCount`、`totalPage` | string |
| `asyncTaskRecord.id` | string |
| `asyncTaskRecord.status`、任务 `status` | number |

⚠️ 不要用 `=== 0` 这类严格比较判断这些数值。

### 3.3 鉴权

- 本服务没有 Spring Security，当前用户由网关注入的 `X-Sfc-Uid` 信任头决定；
- **除网关转发外不要直连 8092**，否则可伪造身份；
- 未登录（缺少该头）时，`createTask`、`capabilities`、`detect-type` 与 SSH 凭据接口返回信封
  `code=401 / msg="请先登录"`（**HTTP 状态仍是 200**）；
- SSH 主机信任记录接口（`/ssh-host-key`）仅管理员可用：未登录 `code=401`，已登录但非管理员
  `code=403 / msg="仅管理员可管理 SSH 主机信任记录"`（**HTTP 状态仍是 200**，管理员判定经 core 回源）。

---

## 四、类型接口：能力声明与地址推导

`GET /capabilities` 告诉前端「本实例当前支持哪些下载类型、各自有什么可选项、不可用的原因是什么」；
`GET /detect-type` 则按用户填写的下载地址给出建议类型，用于自动选中下拉框。

### 4.1 请求

```
GET /api/task/download/capabilities?refresh=false
```

| 参数 | 位置 | 必填 | 默认 | 说明 |
|---|---|---|---|---|
| `refresh` | query | 否 | `false` | `true` 时先强制刷新服务端的统一配置快照（配置默认 5 秒缓存）。**不会**清空 aria2 连通性探测缓存（约 30 秒），因此刚打开 aria2 开关后可能仍需等待其自然过期 |

### 4.2 响应示例

```json
{
  "code": 200,
  "businessCode": 200,
  "msg": null,
  "data": [
    {
      "type": "HTTP",
      "name": "HTTP/HTTPS",
      "engineId": "http",
      "available": true,
      "reason": null,
      "options": []
    },
    {
      "type": "GIT",
      "name": "Git 克隆",
      "engineId": "git",
      "available": true,
      "reason": null,
      "options": [
        { "name": "gitBranch",     "title": "分支/标签",    "inputType": "text",   "sort": "10", "required": false, "describe": "留空表示使用仓库默认分支" },
        { "name": "gitDepth",      "title": "克隆深度",     "inputType": "text",   "sort": "20", "required": false, "defaultValue": "0",     "describe": "整数；0 表示全量克隆，大于 0 时执行浅克隆" },
        { "name": "gitSubmodules", "title": "克隆子模块",   "inputType": "switch", "sort": "30", "required": false, "defaultValue": "false", "describe": "是否一并克隆仓库的子模块" },
        { "name": "stripGitDir",   "title": "删除 .git 目录","inputType": "switch","sort": "40", "required": false, "defaultValue": "true",  "describe": "网盘中只保留工作区文件，不保留版本库元数据" },
        { "name": "gitUsername",   "title": "用户名",       "inputType": "text",   "sort": "50", "required": false, "describe": "私有仓库的 Basic Auth 用户名，与访问令牌成对使用" },
        { "name": "gitToken",      "title": "访问令牌",     "inputType": "text",   "sort": "60", "required": false, "mask": true, "describe": "私有仓库的访问令牌或密码，敏感信息，不会回显" }
      ]
    },
    {
      "type": "MAGNET",
      "name": "磁力链",
      "engineId": "aria2",
      "available": false,
      "reason": "aria2 未启用：请在 core 后台「离线下载」配置中开启，并填写 aria2c 的 RPC 地址与令牌",
      "options": [
        { "name": "aria2Options", "title": "aria2 选项", "inputType": "textarea", "sort": "10", "required": false, "describe": "JSON 对象文本，例如 {\"split\":\"16\"}；键为 aria2 原生选项名，值统一按字符串传入。服务端只放行白名单键：split、max-connection-per-server、min-split-size、bt-tracker、max-download-limit、max-overall-download-limit、continue、user-agent、referer；dir/out 等涉及本地路径的选项由服务端强制控制，用户传入会被丢弃" }
      ]
    },
    {
      "type": "TORRENT",
      "name": "种子",
      "engineId": "aria2",
      "available": false,
      "reason": "aria2 未启用：请在 core 后台「离线下载」配置中开启，并填写 aria2c 的 RPC 地址与令牌",
      "options": [
        { "name": "aria2Options", "title": "aria2 选项", "inputType": "textarea", "sort": "10", "required": false, "describe": "……同 MAGNET……" }
      ]
    },
    {
      "type": "ED2K",
      "name": "ed2k",
      "engineId": "ed2k",
      "available": false,
      "reason": "ed2k 下载引擎尚未接入（预留扩展点，可接入 mldonkey / aMule）",
      "options": []
    }
  ]
}
```

> 为便于阅读，上例省略了值为 `false` 的 `mask` / `readonly` / `disabled` / `hide`；
> 实际响应中这些字段**恒输出**（`gitToken` 的 `mask` 为 `true`）。

### 4.3 字段说明

| 字段 | 类型 | 说明 |
|---|---|---|
| `type` | string | 类型编码，取值 `HTTP` / `GIT` / `MAGNET` / `TORRENT` / `ED2K`（创建任务时原样回传） |
| `name` | string | 展示名（`HTTP/HTTPS`、`Git 克隆`、`磁力链`、`种子`、`ed2k`） |
| `engineId` | string | 承载该类型的下载引擎标识：`http` / `git` / `aria2` / `ed2k`。**仅用于诊断与埋点**，不要用它判断类型；`MAGNET` 与 `TORRENT` 同为 `aria2` |
| `available` | boolean | 当前是否可用；`false` 时不要允许用户选它提交 |
| `reason` | string \| null | 不可用原因（可直接展示）；可用时为 `null` |
| `options` | array | 该类型的可选项元数据，见下 |

`options[]` 元素（每个元素就是一个 `ConfigNode`，与核心配置面板使用同一套表单节点模型）：

| 字段 | 类型 | 说明 |
|---|---|---|
| `name` | string | 选项键，提交时放进请求体的 `options` 对象 |
| `title` | string | 展示名 |
| `inputType` | string | 输入控件：`text`（单行文本，数值类选项也用它，需按数字解析）/ `textarea`（多行文本，内容为 JSON 对象文本）/ `switch`（开关） |
| `describe` | string | 说明文案；无说明时该字段不输出 |
| `defaultValue` | string | **字符串形式**的默认值（如 `"0"`、`"false"`），按 `inputType` 转换；无默认值时该字段不输出 |
| `sort` | string | 展示顺序，**注意为字符串**（本服务把 long 统一序列化为字符串），请按数值升序解析 |
| `mask` | boolean | 是否打码显示；凭证类选项（`gitToken`）为 `true` |
| `required` | boolean | 是否必填（当前所有选项均为 `false`） |
| `readonly` / `disabled` / `hide` | boolean | 只读 / 禁用 / 在菜单中隐藏（当前均为 `false`） |

> ⚠️ `aria2Options` 的 `inputType` 是 `textarea`，取值为 JSON 对象**文本**（如 `"{\"split\":\"16\"}"`），
> 服务端解析成键值对后再按白名单过滤；无法解析成 JSON 对象时返回 `code=400`。
>
> SSH 克隆与凭据管理**不改变能力声明**：`capabilities` 仍返回 5 个类型、`GIT` 的 `options` 不变
> （SSH 凭据是创建任务的**顶层字段**，不在 `options` 中）；SSH 凭据与主机信任记录通过 §九 的独立接口管理。

### 4.4 数量与顺序

- 数组顺序固定为 `HTTP → GIT → MAGNET → TORRENT → ED2K`（只包含已注册引擎的类型）；
- 当前 ed2k 恒为不可用，属预期，不要当作故障；
- 创建任务时 `type` **必填**，前端必须让用户选定（或由 §4.5 自动选定）后再提交；服务端不再替用户识别类型。

### 4.5 按下载地址推导类型：`GET /detect-type`

用户在创建页修改/粘贴下载地址后，前端可以调用本接口自动选中类型（用户仍可手动修改）。接口只做**语法判断**：
不访问 core、不发起网络请求、不判断引擎是否可用（可用性看 §4 的 `capabilities`）。

#### 请求

```
GET /api/task/download/detect-type?url=https%3A%2F%2Fgithub.com%2Fexample%2Fproject.git
```

| 参数 | 位置 | 必填 | 说明 |
|---|---|---|---|
| `url` | query | 否 | 下载地址；为空或无法识别时返回 `data=null`。请对地址做 URL 编码 |

需登录（与 `/capabilities` 一致）：未登录返回信封 `code=401`（HTTP 仍为 200）。

#### 响应

成功识别：

```json
{ "code": 200, "businessCode": 200, "msg": "OK", "data": "GIT" }
```

无法识别（包括 `url` 为空、用户只输入了半截链接）：

```json
{ "code": 200, "businessCode": 200, "msg": "OK", "data": null }
```

> `data` 是**纯字符串**类型编码（`HTTP` / `GIT` / `MAGNET` / `TORRENT` / `ED2K`），可直接赋给类型下拉框。
> 无法识别**不是错误**，不要弹错误提示；前端保持当前选择（或等用户继续输入）即可。

#### 判定规则（纯语法猜测）

| 来源特征 | 推断结果 |
|---|---|
| `magnet:?...` | `MAGNET` |
| `ed2k://...` | `ED2K` |
| `git://...`、`ssh://...`、`git@host:path` | `GIT` |
| `http(s)://...` 且路径（去掉 `?query` 与 `#fragment`）以 `.torrent` 结尾 | `TORRENT` |
| `http(s)://...` 且路径以 `.git` 结尾 | `GIT` |
| 其它 `http(s)://...` | `HTTP` |
| 空 / 无法判定 | `null`（不报错） |

> ⚠️ 推断是**语法猜测**：例如 `https://example.com/repo.git` 也可能只是一个普通文件下载。
> 用户手动改选类型时以用户选择为准，服务端不会覆盖；推断结果只用于「自动选中」这一步。
>
> ⚠️ `ssh://` 与 `git@host:path` 会被推断为 `GIT`，且本服务**支持 SSH 克隆**；但提交时必须在请求体
> **顶层**提供 `sshCredentialId`（见 §5.1 与 §九），否则返回 `code=400`「SSH 地址必须选择 SSH 凭据（sshCredentialId）」。

#### 调用时机建议

- 在 URL 输入框上做**防抖**（如输入停止 300~500ms 后再调用），避免每次按键都请求；
- 只在 URL 非空时调用；返回 `null` 时不改动用户当前选择；
- 用户手动改过类型后，是否继续用推断结果覆盖由前端自行决定（建议：用户手动改过就不再自动覆盖，直到用户清空/重新粘贴地址）。

---

## 五、创建任务：`POST /api/task/download`

### 5.1 请求体字段

| 字段 | 类型 | 必填 | 默认 | 说明 |
|---|---|---|---|---|
| `type` | string | **是** | — | `HTTP` / `GIT` / `MAGNET` / `TORRENT` / `ED2K`，大小写不敏感。**必须显式提交**，服务端不推导；缺失 → HTTP 422 + 信封 `code=422`，非法取值（如 `AUTO`、`FOO`）→ HTTP 400 + 信封 `code=400` |
| `url` | string | 视类型 | — | 下载来源：HTTP 直链、Git 仓库地址、磁力链、ed2k 链接、`.torrent` 文件地址。`TORRENT` 且使用 `torrentBase64` 时可为空 |
| `uid` | number | 是 | — | 保存到哪个用户的网盘；`0` 表示公共网盘（**仅管理员可写**），普通用户只能填自己 |
| `savePath` | string | 是 | — | 保存目录，如 `/download`、`/`；不能为空（为空 → HTTP 422 + 信封 `code=422`） |
| `options` | object | 否 | — | 类型专属参数，键名见 §5.4 |
| `torrentBase64` | string | 否 | — | Base64 编码的 `.torrent` 内容（用于无法提供可访问地址的场景） |
| `proxy` | string | 否 | 直连 | 代理**名称**（在 core 的代理管理中维护）。对 `HTTP` / `MAGNET` / `TORRENT` 与 `GIT`（HTTP(S) 与 SSH 克隆）生效；`git://` 地址不支持代理（带代理返回 `code=400`）；`ED2K` 忽略 |
| `sshCredentialId` | string \| number | 视地址 | — | 用户 SSH 凭据 id（§九）。仅 `type=GIT` 且地址为 SSH（`ssh://...` 或 `user@host:path`）时使用且**必填**；非 SSH 地址不要传。JSON 中数字或字符串均可（本服务 Long 统一序列化为字符串），凭据必须是当前用户自己的 |
| `headers` | object | 否 | — | 附加请求头，仅 HTTP 类型使用；同名时覆盖默认 User-Agent |
| `method` | string | 否 | `GET` | 仅 HTTP 类型使用 |
| `connectTimeout` | number | 否 | `10000` | 毫秒，仅 HTTP 类型使用 |
| `readTimeout` | number | 否 | `60000` | 毫秒，仅 HTTP 类型使用 |

> 服务端还会回填 `downloadId`、`proxyType`、`proxyAddress`、`proxyPort`，客户端**不要**传这些字段。

### 5.2 返回

```json
{ "code": 200, "businessCode": 200, "msg": null, "data": "1837465928374652928" }
```

`data` 是新建任务的 id（字符串）。任务进入异步队列执行，**创建成功不代表下载成功**；结果请看列表接口的 `status` / `message`。

### 5.3 `type` 必填与自动选型流程

服务端**不会**在提交或执行阶段推导类型：

- 请求体缺少 `type` → Bean Validation 失败，HTTP **422**，信封 `code=422`，`msg` 形如 `type: 下载类型不能为空`；
- `type` 传了未定义的值（例如旧版的 `AUTO`、或拼错的 `FOO`）→ 请求体反序列化失败，HTTP **400**，
  信封 `code=400`，`msg` 形如 `请求体格式错误: 不支持的下载类型: FOO`；
- `type` 合法但该类型没有可用引擎（如 ed2k 未接入、aria2 未启用）→ HTTP 200 + 信封 `code=400`，
  `msg` 为可读原因（见 §10.2）。

推荐的前端自动选型流程：

1. 用户输入/粘贴 URL 并停止输入（建议防抖 300~500ms）→ 调 `GET /detect-type?url=<编码后的地址>`（§4.5）；
2. `data` 非空 → 把类型下拉框自动选中该值；`data=null` → 保持当前选择；
3. 用户仍可手动改类型（以用户最终选择为准）；
4. 提交 `POST /api/task/download` 时把选中的类型放进 `type`。

> 注意：`detect-type` 是纯语法猜测（规则见 §4.5），不保证该类型当前可用；提交时仍可能因为
> 「引擎未启用」「链接格式非法」返回 `code=400`，按 §10.2 展示 `msg` 即可。

### 5.4 `options` 键名（与服务端 `DownloadOptionKeys` 一致）

**GIT**

| 键 | 类型 | 说明 |
|---|---|---|
| `gitBranch` | string | 分支或标签，留空用仓库默认分支 |
| `gitDepth` | number | 克隆深度，`0` 或不传表示全量克隆 |
| `gitSubmodules` | boolean | 是否克隆子模块，默认 `false` |
| `stripGitDir` | boolean | 是否删除产物中的 `.git` 目录；不传时取服务端配置（**默认删除**） |
| `gitUsername` / `gitToken` | string | 私有仓库 Basic Auth，**必须成对提供**，否则返回 `code=400` |

**MAGNET / TORRENT**

| 键 | 类型 | 说明 |
|---|---|---|
| `aria2Options` | string | 透传给 aria2 的选项，取值为 JSON 对象**文本**，如 `"{\"split\":\"16\"}"`；对象内的键为 aria2 选项名、值统一按字符串处理。白名单：`split`、`max-connection-per-server`、`min-split-size`、`bt-tracker`、`max-download-limit`、`max-overall-download-limit`、`continue`、`user-agent`、`referer`；白名单外的键会被丢弃，`dir`/`out` 由服务端强制控制 |

> ⚠️ SSH 凭据**不是** `options` 键：它是任务请求体的顶层字段 `sshCredentialId`（§5.1），`GIT` 的 `options`
> 仍只有上表列出的 6 个键。`gitUsername` / `gitToken` 继续用于 HTTP(S) 私有仓库的 Basic Auth；
> SSH 认证请使用用户 SSH 凭据。

### 5.5 示例

**普通 HTTP 直链**

```json
{
  "type": "HTTP",
  "uid": 1,
  "savePath": "/download",
  "url": "https://example.com/ubuntu-24.04.iso",
  "method": "GET",
  "proxy": "香港节点",
  "connectTimeout": 10000,
  "readTimeout": 60000
}
```

**Git 克隆（浅克隆 + 指定分支 + 删除 .git）**

```json
{
  "type": "GIT",
  "uid": 1,
  "savePath": "/download",
  "url": "https://github.com/example/project.git",
  "options": {
    "gitBranch": "main",
    "gitDepth": 1,
    "gitSubmodules": false,
    "stripGitDir": true
  }
}
```

**Git 克隆（SSH + 用户 SSH 凭据）**

```json
{
  "type": "GIT",
  "uid": 1,
  "savePath": "/download",
  "url": "git@github.com:example/project.git",
  "sshCredentialId": "1837465928374652928",
  "options": {
    "gitBranch": "main",
    "gitDepth": 1
  }
}
```

> `ssh://git@git.example.com:2222/example/project.git` 等 `ssh://` 地址同样必须提供 `sshCredentialId`；
> 凭据需先通过 §九 的接口新增或查询。任务参数只保存凭据 id，不会下发私钥/口令/密码。

**Git 克隆（HTTP(S) + 代理）**

```json
{
  "type": "GIT",
  "uid": 1,
  "savePath": "/download",
  "url": "https://github.com/example/project.git",
  "proxy": "香港节点"
}
```

**磁力链**

```json
{
  "type": "MAGNET",
  "uid": 1,
  "savePath": "/download",
  "url": "magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567&dn=example",
  "options": {
    "aria2Options": "{\"split\":\"16\",\"max-connection-per-server\":\"16\"}"
  }
}
```

**种子：以 `.torrent` 地址提交**

```json
{
  "type": "TORRENT",
  "uid": 1,
  "savePath": "/download",
  "url": "https://example.com/ubuntu-24.04.torrent"
}
```

**种子：上传本地 `.torrent` 文件后以 Base64 提交**

```json
{
  "type": "TORRENT",
  "uid": 1,
  "savePath": "/download",
  "torrentBase64": "ZAgAAAAAAAQAAAA..."
}
```

前端读取 `.torrent` 文件为 Base64 的写法（**不要**用 `readAsDataURL`，它会带 `data:...;base64,` 前缀）：

```js
/**
 * 把本地 .torrent 文件读成纯 Base64 字符串（不含 data URL 前缀）。
 * @param {File} file 用户选择的 .torrent 文件
 * @returns {Promise<string>} Base64 内容
 */
async function readTorrentAsBase64(file) {
  const buffer = await file.arrayBuffer();
  const bytes = new Uint8Array(buffer);
  let binary = '';
  const CHUNK = 0x8000; // 分块避免 fromCharCode 参数过多
  for (let i = 0; i < bytes.length; i += CHUNK) {
    binary += String.fromCharCode.apply(null, bytes.subarray(i, i + CHUNK));
  }
  return btoa(binary);
}
```

> 体积上限由服务端配置 `download.aria2.torrent-max-size` 控制（默认 **2 MB**，按 Base64 长度折算字节），超限返回 `code=400`「种子文件超过上限 N MB」。前端建议在上传前就做体积校验并提示用户改用 `.torrent` 地址。

### 5.6 服务端校验顺序（决定你会先收到哪条错误）

1. 校验 `type` 必填（缺失 → 422）与取值合法（未定义取值 → 400）；
2. **统一校验**：类型是否有引擎 → 引擎当前是否可用（如 aria2 是否启用/可达）→ 来源协议/格式是否合法 → 引擎专属参数；
   `GIT` 的跨字段约束也在此处判定：SSH 地址必须携带 `sshCredentialId`、非 SSH 地址不得携带、`git://` 地址不得带代理；
3. 校验 `sshCredentialId`（本地数据库查询，先于需要回源 core 的步骤）：已提供时，非 `GIT` 类型直接拒绝
   （`仅 Git SSH 克隆支持选择 SSH 凭据`）；`GIT` 类型校验凭据存在且属于当前创建者，私钥认证还要求
   SSH 地址或凭据中至少有一个用户名；
4. 校验 `savePath` 是否存在且可访问（回源 core）；
5. 解析 `proxy`（HTTP / GIT / MAGNET / TORRENT；`git://` 地址带代理 → 400）；
6. 落库并向 core 提交异步任务。

因此**类型相关的业务错误（`code=400`，HTTP 仍为 200）会先于「无效的保存路径」返回**，便于用户先修对类型/链接。

---

## 六、任务列表：`GET /api/task/download`

### 6.1 请求参数

| 参数 | 必填 | 默认 | 说明 |
|---|---|---|---|
| `uid` | 是 | — | 任务归属用户；只能查本人或（管理员）他人 |
| `page` | 否 | `1` | 页码，从 1 开始 |
| `size` | 否 | `10` | 每页数量，取值 5~400 |
| `type` | 否 | `ALL` | **状态筛选**（与下载类型无关）：`ALL` / `DOWNLOADING` / `FINISH` / `FAILED` |

> ⚠️ 查询参数 `type` 是**状态筛选**，请求体里的 `type` 是**下载类型**，两者同名但语义不同，不要混用。

### 6.2 响应示例

```json
{
  "code": 200,
  "businessCode": 200,
  "msg": null,
  "data": {
    "content": [
      {
        "id": "1837465928374652928",
        "uid": "1",
        "createdBy": "1",
        "type": "GIT",
        "url": "https://github.com/example/project.git",
        "proxy": null,
        "message": null,
        "name": "project",
        "savePath": "/download",
        "size": "1048576",
        "loaded": "1048576",
        "speed": "0",
        "status": 2,
        "taskId": "9988776655443322110",
        "finishAt": "2026-10-26 12:00:00",
        "createdAt": "2026-10-26 11:59:00",
        "asyncTaskRecord": { "id": "9988776655443322110", "status": 2 }
      }
    ],
    "totalCount": "1",
    "totalPage": "1"
  }
}
```

### 6.3 字段说明

| 字段 | 说明 |
|---|---|
| `id` / `taskId` / `asyncTaskRecord.id` | 字符串形式的 id |
| `type` | 下载类型编码（创建时提交的值）；升级前创建的历史记录该列为空，返回 `null` |
| `name` | 保存到网盘的文件名**或顶层目录名**（多文件任务为目录名） |
| `status` | 状态镜像：`0` 等待、`1` 运行、`2` 完成、`3` 失败、`4` 取消、`5` 离线 |
| `asyncTaskRecord.status` | 与 `status` 同源；列表接口会用 core 的实时状态校准非终态任务 |
| `message` | 失败原因（成功时为空） |
| `size` / `loaded` / `speed` | 总字节数 / 已下载字节数 / 速度（字符串） |
| `finishAt` / `createdAt` | 时间字符串 |

### 6.4 展示建议

- 用 `type` 渲染类型标签（如 `HTTP`→链接图标、`GIT`→分支图标、`MAGNET`/`TORRENT`→磁铁图标、`ED2K`→驴图标）；`type` 为 `null` 的历史记录请给一个通用图标/「未知」标签；
- `name` 可能是**目录**（BT 多文件、Git 仓库），点击跳转时请按目录处理，或先查询该路径的文件列表；
- `status` 为 `3/4/5` 时展示 `message`（可能是 aria2/Git 的原始错误，内容较长）。

### 6.5 保存结果的形态

| 场景 | 网盘结果 |
|---|---|
| HTTP 单文件 | 直接落在 `savePath` 下，文件名取自响应头或 URL；重名时追加 `.1`、`.2`… |
| Git 仓库 | 在 `savePath` 下生成一个**顶层目录**（仓库名，重名同样追加 `.1`）；默认已删除 `.git` |
| BT 单文件种子 | 同 HTTP 单文件 |
| BT 多文件种子 | 在 `savePath` 下生成一个**顶层目录**（种子信息里的名称） |

---

## 七、中断任务：`DELETE /api/task/download?taskId=...`

| 信封 `code` | 说明 |
|---|---|
| 200 | 中断指令已下发 |
| 400 | 任务不在「等待中 / 运行中」，或尚未提交到异步任务系统 |
| 403 | 不是任务创建者且不是管理员（该异常携带 `SfcErrorInfo`，**HTTP 状态也会是 403**） |
| 404 | 下载任务不存在 |

> 除 403 外，上表其余结果的 **HTTP 状态均为 200**，请以信封 `code` 判定结果。
> 旧前端多传的 `uid` 查询参数会被忽略，不影响调用。

---

## 八、代理列表：`GET /api/task/download/proxy`

返回当前用户可见的代理节点：

```json
{
  "code": 200,
  "businessCode": 200,
  "msg": null,
  "data": [ { "id": "1", "name": "香港节点", "type": "HTTP", "address": "127.0.0.1", "port": 1080 } ]
}
```

`createTask` 的 `proxy` 传的是这里的 **`name`**（不是 `id`）。未登录返回空数组。

可见范围由 core 的代理管理服务决定：公共代理 + 本人代理；受保护的公共代理仅管理员可见。

代理的生效范围：`HTTP`、`MAGNET`、`TORRENT` 以及 `GIT`（HTTP(S) 克隆与 SSH 克隆都支持 HTTP 代理与 SOCKS（SOCKS5）代理）；
`git://` 地址不支持代理，带代理创建会返回 `code=400`「git:// 协议不支持代理，请改用 http(s) 或 ssh 地址」；
`ED2K` 忽略代理。代理地址/端口在创建任务时快照，之后修改或删除 core 的代理配置不影响已创建任务。

---

## 九、SSH 凭据与主机信任记录管理

Git 的 SSH 克隆需要**用户级 SSH 凭据**；SSH 主机密钥采用 **TOFU（首次信任）** 策略，管理员可维护信任记录。
凭据与信任记录都存放在本服务 SQLite（`ssh_credential` / `ssh_host_key`），本服务**不读取宿主机 `~/.ssh`**
（密钥、`known_hosts`、`config` 均不读取），SSH 认证完全由用户凭据驱动。

### 9.1 用户 SSH 凭据：`/ssh-credential`

| 方法 | 路径（相对 `/api/task/download`） | 权限 | 说明 |
|---|---|---|---|
| GET | `/ssh-credential` | 登录用户 | 当前用户凭据列表（按 id 倒序） |
| POST | `/ssh-credential` | 登录用户 | 新增凭据，返回新增后的凭据元信息 |
| DELETE | `/ssh-credential?id=` | 登录用户 | 删除本人凭据 |

#### 列表

```
GET /api/task/download/ssh-credential
```

响应示例：

```json
{
  "code": 200,
  "businessCode": 200,
  "msg": null,
  "data": [
    {
      "id": "123",
      "name": "GitHub 部署密钥",
      "authType": "PRIVATE_KEY",
      "username": "git",
      "fingerprint": "SHA256:xxxx",
      "createAt": "2026-10-28 10:00:00"
    },
    {
      "id": "124",
      "name": "内网 GitLab 密码",
      "authType": "PASSWORD",
      "username": "git",
      "fingerprint": null,
      "createAt": "2026-10-28 10:05:00"
    }
  ]
}
```

> 返回中**绝不包含** `privateKey` / `passphrase` / `password`；`fingerprint` 仅私钥方式有值。
> 未登录时 HTTP 仍为 200，信封 `code=401`。

#### 新增

请求体字段：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `name` | string | 是 | 凭据名称，≤128 字符 |
| `authType` | string | 是 | `PRIVATE_KEY`（私钥）或 `PASSWORD`（密码） |
| `username` | string | 视方式 | SSH 用户名；`PASSWORD` 方式必填。地址自带用户名（如 `git@host:path`）时一般无需再填；地址未带用户名（如 `ssh://host/path`）时用本字段补全 |
| `privateKey` | string | 视方式 | OpenSSH / PEM 私钥文本；`PRIVATE_KEY` 方式必填 |
| `passphrase` | string | 否 | 加密私钥的口令，仅 `PRIVATE_KEY` 方式使用 |
| `password` | string | 视方式 | SSH 密码；`PASSWORD` 方式必填 |

私钥方式示例：

```json
{
  "name": "GitHub 部署密钥",
  "authType": "PRIVATE_KEY",
  "username": "git",
  "privateKey": "-----BEGIN OPENSSH PRIVATE KEY-----\n...\n-----END OPENSSH PRIVATE KEY-----",
  "passphrase": "可选：加密私钥口令"
}
```

密码方式示例：

```json
{ "name": "内网 GitLab 密码", "authType": "PASSWORD", "username": "git", "password": "******" }
```

保存时校验：私钥必须可解析（加密私钥需提供正确口令）并自动计算公钥指纹 `SHA256:...`；`PASSWORD` 方式必须
提供 `username` + `password`。失败返回 `code=400` 与可读 `msg`（如 `SSH 私钥解析失败: <原因>`、
`SSH 密码认证需要提供用户名`）；`name` / `authType` 缺失等 Bean Validation 失败返回 HTTP 422。
成功响应的 `data` 为新增后的凭据元信息（结构同列表项）。

#### 删除

```
DELETE /api/task/download/ssh-credential?id=123
```

成功返回空信封（`data=null`）。凭据不存在或不属于当前用户时返回 `code=400`「无效的 SSH 凭据: 123」；
`id` 缺失 → HTTP 422，类型不合法（如 `id=abc`）→ HTTP 400。

> 凭据管理**没有更新（编辑）接口**：修改凭据请删除后重新新增。删除正在被任务使用的凭据后，
> 该任务在执行时会失败并在 `message` 中提示「SSH 凭据不存在或已被删除: {id}」。

### 9.2 主机信任记录（管理员）：`/ssh-host-key`

SSH 首次连接未知主机时自动接受，并把主机密钥写入信任记录（TOFU，**全局共享**，不按用户隔离）；
之后同一主机指纹发生变化时克隆失败，管理员删除旧记录后重试即可重新信任。信任记录由克隆过程自动写入，
本接口只提供人工维护能力。

| 方法 | 路径（相对 `/api/task/download`） | 权限 | 说明 |
|---|---|---|---|
| GET | `/ssh-host-key?connectAddress=` | 管理员 | 信任记录列表；`connectAddress` 可选**精确**过滤（`host` 或 `host:port`） |
| DELETE | `/ssh-host-key?id=` | 管理员 | 删除信任记录；记录不存在返回 `code=404` |

响应示例：

```json
{
  "code": 200,
  "businessCode": 200,
  "msg": null,
  "data": [
    {
      "id": "1",
      "connectAddress": "github.com",
      "keyType": "ssh-ed25519",
      "publicKey": "ssh-ed25519 AAAAC3Nza...",
      "fingerprint": "SHA256:xxxx",
      "uid": "1",
      "createAt": "2026-10-28 10:10:00"
    }
  ]
}
```

- `connectAddress`：标准端口为 `host`，非标准端口为 `host:port`（如 `git.example.com:2222`）；
- `uid`：首次信任该主机密钥的用户 uid，用于审计；
- 公钥本身不是机密信息，展示出来便于管理员人工核对指纹。

权限与错误：未登录 `code=401`；非管理员 `code=403`、`msg="仅管理员可管理 SSH 主机信任记录"`
（**HTTP 仍为 200**）。删除时 `id` 非法 → `code=400`，记录不存在 → `code=404`
「SSH 主机信任记录不存在: {id}」。

> ⚠️ 主机密钥变化**可能是主机轮换密钥，也可能是中间人攻击**。只有确认是前者时才删除旧记录并重新信任。

### 9.3 前端交互建议

- **创建页**：类型为 `GIT` 且 URL 以 `ssh://` 开头或匹配 `user@host:path`（含 `git@...`）时，展示「SSH 凭据」
  下拉框（数据来自 `GET /ssh-credential`）；用户选中后把凭据 `id` 放进请求体**顶层** `sshCredentialId`；
- URL 不是 SSH 形式时隐藏该下拉框，且**不要提交** `sshCredentialId`（否则 `code=400`）；
- 无可用凭据时提供「新增凭据」入口：表单按 `authType` 切换字段（私钥方式：`privateKey` + 可选 `passphrase`；
  密码方式：`username` + `password`），提交成功后刷新列表并自动选中新凭据；
- 凭据列表只能展示 `name` / `authType` / `username` / `fingerprint` / `createAt`，私钥/口令/密码不会回显；
  修改凭据 = 删除 + 新增（没有编辑接口）；
- **管理员端**可提供主机信任记录页面：按 `connectAddress` 查询、删除（删除后提示「下次克隆将重新信任该主机」）；
  普通用户不展示该入口（调用会得到信封 `code=403`）。

## 十、错误码与常见消息

### 10.1 业务码（信封 `code`）与 HTTP 状态

> ⚠️ **判定业务结果请一律读信封 `code`，不要用 HTTP 状态码。**

| 信封 `code` | 含义 |
|---|---|
| 200 | 成功 |
| 400 | 客户端参数/来源/能力问题（类型不支持、链接格式非法、引擎未启用等） |
| 401 | 未登录（缺少 `X-Sfc-Uid`；含 SSH 凭据 / 主机信任记录接口） |
| 403 | 权限不足：资源级校验（如非创建者中断任务）或非管理员访问 SSH 主机信任记录接口 |
| 404 | 资源不存在（下载任务、SSH 主机信任记录） |
| 422 | Bean Validation 失败（`type`/`savePath` 为空、`uid` 无权、`page`/`size` 越界） |
| 500 | 服务端未预期异常（core 不可用、内部错误） |

**HTTP 状态码只在下列场景不是 200**（由 `SfcDownloadExceptionAdvice` 决定）：

| 场景 | HTTP 状态 | 信封 `code` |
|---|---|---|
| 请求体无法反序列化（如 `"type":"FOO"` 或 `"type":"AUTO"`） | **400** | 400 |
| 查询参数类型不匹配（如列表 `?type=FOO`） | **400** | 400 |
| Bean Validation 失败（如缺 `type`、`savePath` 为空、`size=1`） | **422** | 422 |
| 资源级权限不足（携带 `SfcErrorInfo` 的业务异常，如非创建者中断） | **403** | 403 |

> 其余业务失败（未登录、任务不存在、引擎未启用、链接非法、非管理员访问 SSH 主机信任记录……）
> **HTTP 状态均为 200**，只改信封 `code`。
>
> 实测示例：
> - `GET /capabilities`、`GET /detect-type` 不带 `X-Sfc-Uid` → **HTTP 200**，信封 `code=401`、`msg="请先登录"`；
> - `GET /api/task/download/ssh-credential` 不带 `X-Sfc-Uid` → **HTTP 200**，信封 `code=401`；
> - 普通用户 `GET /api/task/download/ssh-host-key` → **HTTP 200**，信封 `code=403`、`msg="仅管理员可管理 SSH 主机信任记录"`；
> - `DELETE /api/task/download?taskId=999999999` → **HTTP 200**，信封 `code=404`；
> - 创建 ed2k 任务、或 aria2 未启用时创建磁力链 → **HTTP 200**，信封 `code=400` + 可读 `msg`；
> - 创建任务传 `"type":"FOO"` 或 `"type":"AUTO"` → **HTTP 400**（请求体无法反序列化）；
> - 创建任务不传 `type` → **HTTP 422**，信封 `code=422`。

### 10.2 常见文案（可直接展示给用户）

| 场景 | 信封 `code` | 典型 `msg` |
|---|---|---|
| 未登录 | 401（HTTP 200） | `请先登录` |
| `uid` 无权（创建时） | 422（HTTP 422） | `uid: 无权操作资源` |
| 非创建者/管理员中断（中断时） | 403（HTTP 403） | `权限不足` |
| 缺 `type`（创建时） | 422（HTTP 422） | `type: 下载类型不能为空` |
| `type` 传 `AUTO` / 未知值 | 400（HTTP 400） | `请求体格式错误: 不支持的下载类型: AUTO` |
| 非法类型编码 | 400（HTTP 400） | `请求体格式错误: 不支持的下载类型: FOO` |
| 类型无引擎 | 400 | `不支持的下载类型: FOO` |
| ed2k | 400 | `ed2k 下载引擎尚未接入，当前支持 HTTP / GIT / MAGNET / TORRENT` |
| aria2 未启用 | 400 | `aria2 未启用：请在 core 后台「离线下载」配置中开启，并填写 aria2c 的 RPC 地址与令牌` |
| aria2 不可达 | 400 | `aria2 RPC 不可达（http://127.0.0.1:6800/jsonrpc）：<原因>` |
| 磁力链格式错 | 400 | `无效的磁力链: <原文>` |
| ed2k 链接格式错 | 400 | `无效的 ed2k 链接: <原文>` |
| HTTP 地址非法 | 400 | `无效的下载URL: <原文>` |
| SSH 地址未选择凭据 | 400 | `SSH 地址必须选择 SSH 凭据（sshCredentialId）` |
| 非 SSH 地址传了 `sshCredentialId` | 400 | `仅 SSH 地址支持选择 SSH 凭据` |
| 非 `GIT` 类型传了 `sshCredentialId` | 400 | `仅 Git SSH 克隆支持选择 SSH 凭据` |
| SSH 凭据不存在 / 非本人 / 无效 | 400 | `无效的 SSH 凭据: 123` |
| 私钥认证且 SSH 地址未带用户名、凭据也未配置 `username` | 400 | `SSH 地址未携带用户名，请在 SSH 凭据中配置用户名` |
| `git://` 地址带代理 | 400 | `git:// 协议不支持代理，请改用 http(s) 或 ssh 地址` |
| 新增凭据缺 `name` / `authType` | 422（HTTP 422） | `name: SSH 凭据名称不能为空` / `authType: SSH 认证方式不能为空` |
| SSH 私钥无法解析 | 400 | `SSH 私钥解析失败: <原因>` |
| 密码方式缺用户名 / 密码 | 400 | `SSH 密码认证需要提供用户名` / `SSH 密码不能为空` |
| SSH 凭据接口未登录 | 401（HTTP 200） | `请先登录` |
| 非管理员访问主机信任记录 | 403（HTTP 200） | `仅管理员可管理 SSH 主机信任记录` |
| 删除不存在的信任记录 | 404（HTTP 200） | `SSH 主机信任记录不存在: 1` |
| Git 用本地协议 | 400 | `不支持本地文件协议: <原文>` |
| Git 地址非法 | 400 | `无效的 Git 仓库地址: <原文>` |
| SSH Git 地址非法 | 400 | `无效的 SSH Git 仓库地址: <原文>` |
| 种子缺来源 | 400 | `种子下载需要 .torrent 文件地址或种子文件内容（torrentBase64），当前来源: <原文>` |
| 种子超限 | 400 | `种子文件超过上限 N MB` |
| Git 凭证不成对 | 400 | `参数 用户名(gitUsername) 与 访问令牌(gitToken) 必须成对提供` |
| 选项取值非法 | 400 | `参数 克隆深度(gitDepth) 取值非法: <值>` |
| 保存路径无效 | 400 | `无效的保存路径:/xxx` |
| 代理不存在 | 400 | `无效的代理：<名称>` |
| 列表筛选参数非法 | 400（HTTP 400） | `参数 type 的取值非法: FOO` |
| 任务不存在 | 404（HTTP 200） | `下载任务不存在: 123` |

### 10.3 执行期失败（不在创建接口体现）

任务执行失败时，`status=3`，`message` 里是执行引擎的原始原因，例如：

- `Git 克隆失败: <原因>`
- `SSH 凭据不存在或已被删除: {id}`（创建任务后、执行前凭据被删除）
- `aria2 RPC 调用失败: <原因>` / `aria2 下载失败: <原因>`
- `aria2 下载超时: N 秒无进度（可能没有可用节点）`
- `下载失败: HTTP 404 Not Found`
- `Git 克隆完成但未采集到任何文件: <repo>`
- SSH 主机密钥与信任记录不一致时，克隆失败并给出可读提示（管理员在信任记录中删除旧记录后可重新信任）

前端展示时建议限制 `message` 展示长度并提供「查看详情」（core 的异步任务日志里有完整过程）。

---

## 十一、迁移与兼容

1. **旧请求体不再兼容**：`{url, uid, savePath, headers?, method?, proxy?, connectTimeout?, readTimeout?}`（不带 `type`）会被拒绝（HTTP 422）。前端必须补上 `type`，需要自动选型时调用 §4.5 的 `detect-type`。
2. **列表的 `type` 字段**：创建时提交什么就返回什么；升级前的历史记录该字段为 `null`；分页结构仍是 `SfcPageInfo{content, totalCount, totalPage}`。
3. **前端编译产物**：本服务 `src/main/resources/static/` 下的 `index.umd.js` / `style.css` 仍是旧版本，不含本指南描述的改动。前端需在**前端仓库**完成改造后重新构建，把产物输出到：
   ```
   microservices/sfc-download-service/src/main/resources/static/
   ```
   然后重启本服务（core 会通过注册中心 metadata `sfc.plugin.autoload` 重新拉取 `/static/index.umd.js`）。
4. **本服务未提供插件配置面板**（离线下载的 aria2/Git 参数由管理员在 core 后台的「插件配置 → 离线下载」里维护）。
5. **不要用 HTTP 状态码判断业务失败**：业务异常默认返回 **HTTP 200 + 信封 `code`**，只有请求体解析失败（HTTP 400）、参数校验失败（HTTP 422）与携带 `SfcErrorInfo` 的资源级权限异常（HTTP 403，如非创建者中断任务）会改写 HTTP 状态。请统一按 `body.code === 200` 判断成功、失败时展示 `body.msg`；这与宿主 `sfc-core` 的既有行为一致。
6. **SSH 克隆与 GIT 代理为新增能力 / 行为变化**：
   - 新增顶层字段 `sshCredentialId`（仅 `GIT` + SSH 地址使用），不影响其它请求体；SSH 地址不提交该字段会被 `code=400` 拒绝；
   - `proxy` 对 `GIT` 由「忽略」改为「生效」：旧前端若对 GIT 任务传了代理，现在会真正走代理；需要直连时不要传 `proxy`；
   - `GET /ssh-credential`、`POST /ssh-credential`、`DELETE /ssh-credential` 为用户接口；`/ssh-host-key` 的查询与删除为管理员接口。

---

## 十二、联调前置

| 依赖 | 要求 |
|---|---|
| `sfc-core` | 已启动并注册（local registry 或 Nacos，`group=SFC`、`namespace` 为空串） |
| `sfc-download-service` | 已启动（8092）并注册；Worker 日志显示 `offline-download` 类型已声明 |
| `sfc-gateway` | 已启动；默认动态路由生效即可（无需为本服务加显式路由） |
| `SFC_INTERNAL_KEY` | core 与本服务必须是同一份（≥32 字符），否则任务提交/状态校准/中断全部失败 |
| 登录态 | 需要 `X-Sfc-Uid`（由网关注入） |
| aria2（可选） | 未部署时 `MAGNET` / `TORRENT` 在 `/capabilities` 中显示不可用，**这是正常状态，不是缺陷** |

### 12.1 用 aria2 打开 P2P 能力

管理员在 core 后台「插件配置 → 离线下载」中配置：

| 配置项 | 示例 |
|---|---|
| `download.aria2.enabled` | `true` |
| `download.aria2.rpc-url` | `http://127.0.0.1:6800/jsonrpc` |
| `download.aria2.secret` | 与 `aria2c --rpc-secret` 保持一致 |

aria2c 启动示例（需与本服务**同机**，服务会把每个任务的 `dir` 指定到自己的临时目录）：

```bash
aria2c --enable-rpc --rpc-listen-all=false --rpc-listen-port=6800 --rpc-secret=<令牌>
```

配置保存后最长约 **5 秒**（统一配置缓存）+ **30 秒**（连通性探测缓存）生效；急着验证可调 `GET /capabilities?refresh=true` 并用 `available` 判断。

---

## 十三、常见问题

**Q：创建任务返回 422「type: 下载类型不能为空」？**
A：`type` 已改为必填，服务端不再按链接自动识别。前端需在 URL 变化时调用 `GET /detect-type?url=` 自动选型（§4.5），或让用户手动选择，然后随请求提交 `type`。

**Q：`/capabilities` 返回空数组？**
A：说明本实例没有任何下载引擎注册成功（通常意味着启动异常），需要看服务端日志，不是前端问题。

**Q：创建磁力链返回 `code=400`「aria2 未启用」？**
A：P2P 下载依赖外部 aria2 进程，需管理员按 §12.1 配置；前端直接把 `msg` 展示给用户即可。

**Q：Git 克隆进度条看起来不准？**
A：Git 任务的进度单位是 JGit 的「工作单元」而不是字节，进度比例准确但总量与文件大小无关；完成后的 `size` 才是真实字节数。

**Q：多文件任务点进去是目录打不开？**
A：见 §6.4，`name` 可能是目录，需要按目录处理。

**Q：为什么 ed2k 一直不可用？**
A：本期只登记了类型和预留扩展点，未接入真实 ed2k 引擎（计划接入 mldonkey / aMule），提交会明确返回 `code=400`。

**Q：Git 可以用 SSH 地址克隆吗？**
A：可以。`ssh://host[:port]/path` 与 SCP 风格 `git@host:owner/repo.git` 都支持，但必须在请求体**顶层**提供
`sshCredentialId`（先通过 `GET /ssh-credential` 选择或 `POST` 新增凭据），否则返回 `code=400`
「SSH 地址必须选择 SSH 凭据（sshCredentialId）」。`file://` / `ext::` / 本地路径等仍按原有规则拒绝。

**Q：SSH 克隆失败，提示主机密钥不一致？**
A：本服务对 SSH 主机密钥采用 TOFU（首次连接自动信任并记录）。若确认主机确实更换了密钥，管理员在
`GET/DELETE /ssh-host-key` 中删除该主机的旧记录后重试即可重新信任；若无法确认，请不要直接删除
（也可能是中间人攻击）。

**Q：Git 任务传了 `proxy` 为什么没生效 / 现在反而生效了？**
A：`proxy` 对 `GIT` 已生效（HTTP(S) 克隆与 SSH 克隆均支持 HTTP 代理与 SOCKS（SOCKS5）代理，地址在创建时快照）；
`git://` 地址不支持代理，带代理创建会返回 `code=400`。

**Q：接口返回 HTTP 200，但前端提示失败了 / 该怎么判断成功？**
A：本服务的业务失败默认返回 **HTTP 200 + 信封 `code`**（如 `code=400/401/404`），错误码不在 HTTP 状态里。
**请判断 `body.code === 200`，不要写 `if (response.status === 400)`**；失败时展示 `body.msg`。
只有请求体无法解析（HTTP 400）、参数校验失败（HTTP 422）与携带 `SfcErrorInfo` 的资源级权限异常（HTTP 403，
如非创建者中断任务）会返回非 200——这也意味着**不能用 HTTP 状态是否会抛出异常来区分业务失败**，需要按响应体判定。
该行为与宿主 `sfc-core` 一致。
