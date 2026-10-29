# 离线下载 · Git 代理与 SSH 克隆 前端对接说明

> 适用对象：负责「离线下载」相关页面的前端工程（主 SPA / 后台插件配置面板）。
> 对应后端版本：`microservices/sfc-download-service`（Git 代理 + SSH 克隆改造）。
> 关联文档：[多类型下载前端对接指南](./frontend-adapter-guide.md)、[前端适配清单](./frontend-adaptation-checklist.md)、
> [离线下载能力说明](../../../docs/extension/download.md)。

本文只覆盖**本次改造新增/变化**的契约：Git 代理生效、SSH 克隆、用户级 SSH 凭据管理、
管理员 SSH 主机信任记录，以及后台「插件配置」面板新增的 SSH 凭据管理组件（template）。

---

## 一、本次变更总览

| # | 变更 | 类型 | 前端需要做的事 |
|---|---|---|---|
| 1 | `GIT` 类型开始**消费代理**（此前提交的 `proxy` 会被忽略并清空） | 行为变化 | Git 任务创建表单的代理下拉改为对所有类型生效；`git://` 地址不允许选代理 |
| 2 | 支持 **SSH 克隆**：`ssh://host[:port]/path`、SCP 风格 `git@host:owner/repo.git` | 新能力 | 地址识别 SSH 时展示「SSH 凭据」下拉并提交 `sshCredentialId` |
| 3 | **用户级 SSH 凭据**管理（私钥 / 带口令私钥 / 密码） | 新接口 ×3 | 凭据列表 + 新增表单 + 删除（无编辑接口） |
| 4 | **管理员**维护 SSH 主机密钥信任记录（TOFU） | 新接口 ×2 | 管理员页面/页签：列表、按主机过滤、删除 |
| 5 | 创建任务新增**顶层字段** `sshCredentialId`（不是 `options` 键） | 契约变化 | 提交时放在请求体顶层；SSH 地址必填、非 SSH 地址不得携带 |
| 6 | 后台「插件配置 → 离线下载」新增菜单 **SSH 凭据管理**，`inputType=template`，组件名 `download-ssh-credential-manager` | 新组件 | 按前端 template 规范注册该 Vue 组件（建议复用 §2.2/§2.3 的逻辑） |

**不在本次范围**：凭据编辑（更新）接口、手动新增主机信任记录、主机密钥严格模式开关、
按主机批量删除信任记录。前端不要实现这些入口。

---

## 二、页面与交互设计

### 2.1 下载任务创建表单（`type=GIT`）

**① 地址类型识别（决定是否显示 SSH 凭据）**

纯前端判断即可（与服务端 `isSshGitUrl` 口径一致）：

- `ssh://` 开头 → SSH；
- 形如 `用户名@主机:路径`（正则：`^[\w.\-]+@[\w.\-]+:.+$`）→ SCP 风格 SSH；
- `http(s)://`、`git://` → 非 SSH。

也可调用 `GET /api/task/download/detect-type?url=` 辅助（SSH 地址会返回 `GIT`），
但「是否 SSH」仍需按上述规则判断（`detect-type` 只给下载类型，不区分协议）。

**② SSH 凭据下拉（仅 SSH 地址显示，且必填）**

- 数据源：`GET /api/task/download/ssh-credential`（当前登录用户的凭据）。
- 展示：`name`（主文案）+ `authType` 标签（私钥/密码）+ 指纹短串（可选，便于区分同名凭据）。
- 空态：显示「还没有 SSH 凭据」+「新增凭据」按钮，点击打开 §2.2 的新增表单；
  新增成功后自动刷新列表并选中新凭据。
- 提交时把选中凭据 id 放到请求体**顶层** `sshCredentialId`（字符串或数字均可，响应侧 id 一律为字符串）。

**③ 用户名规则提示（前端可提前校验，服务端兜底）**

私钥认证时，SSH 会话用户名来自仓库地址；若地址未携带用户名（如 `ssh://host/owner/repo.git`），
则必须在凭据里配置 `username`，否则服务端返回 400
「SSH 地址未携带用户名，请在 SSH 凭据中配置用户名」。
前端可在选中凭据后判断：地址无用户名 && 凭据 `username` 为空 → 提示用户「请改用带用户名的地址，
或选择配置了用户名的凭据」，但**不要**自行改写地址。

**④ 代理下拉**

- `proxy` 是**代理名称**（来自 `GET /api/task/download/proxy`），不是地址；服务端按名称解析并快照。
- 对 `GIT` 现在同样生效：HTTP(S) 克隆与 SSH 克隆都支持 HTTP / SOCKS 代理。
- `git://` 地址不支持代理：地址切换为 `git://` 时禁用/隐藏代理下拉并提示，
  服务端会返回 400「git:// 协议不支持代理，请改用 http(s) 或 ssh 地址」。

**⑤ 提交示例**

SSH 克隆（私钥凭据）：

```jsonc
{
  "type": "GIT",
  "url": "git@github.com:owner/private-repo.git",
  "uid": "1",
  "savePath": "/download/repo",
  "sshCredentialId": "1312924820559626240",   // 顶层字段，不是 options
  "options": {
    "gitBranch": "main",
    "gitDepth": "0",
    "gitSubmodules": false,
    "stripGitDir": true
  }
}
```

HTTPS 克隆 + 代理：

```jsonc
{
  "type": "GIT",
  "url": "https://github.com/owner/repo.git",
  "uid": "1",
  "savePath": "/download/repo",
  "proxy": "公司 HTTP 代理",                   // 代理名称
  "options": { "gitDepth": "1" }
}
```

> `options` 中的 `gitUsername` / `gitToken` 仍只用于 HTTP(S) 私有仓库的 Basic Auth；
> SSH 认证不使用这两个字段。

### 2.2 SSH 凭据管理（所有登录用户）

建议入口：下载页工具栏「SSH 凭据」按钮（抽屉/弹窗）或独立页面。同一套逻辑也用于后台配置面板组件。

**列表**

| 列 | 字段 | 说明 |
|---|---|---|
| 名称 | `name` | 用户可见名称（可重名） |
| 认证方式 | `authType` | `PRIVATE_KEY` 显示「私钥」，`PASSWORD` 显示「密码」 |
| 用户名 | `username` | 可能为空（私钥方式且地址自带用户名时可不填） |
| 指纹 | `fingerprint` | `SHA256:...`；密码方式为空 |
| 创建时间 | `createAt` | ISO-8601 字符串 |

列表**不包含**私钥/口令/密码，前端也不需要请求这些字段。

**新增表单**

- 名称 `name`：必填，≤128 字符；
- 认证方式 `authType`：单选（私钥 / 密码），默认私钥；
- 私钥方式：
  - `privateKey`：多行文本，必填（OpenSSH 或 PEM 格式，≤32KB）；
  - `passphrase`：可选，加密私钥的口令（**不要 trim**，原样提交）；
  - `username`：可选（地址未带用户名时必填）。
- 密码方式：
  - `username`：必填；
  - `password`：必填（**不要 trim**）。
- 提交成功后展示返回的 `fingerprint`，并刷新列表；
- 私钥/口令/密码输入框使用密码型控件或「显示/隐藏」切换，页面不要回显历史值。

**删除**

二次确认后 `DELETE .../ssh-credential?id=`。没有编辑接口：需要修改时删除后重新新增。
提示文案建议：「删除后，已创建但尚未执行的 SSH 克隆任务会失败；正在执行的任务不受影响」。

**安全提示文案（建议在新增表单下方展示）**

> 私钥/口令/密码以明文保存在离线下载服务本地数据库（`download.db`），接口不会回显、日志已脱敏。
> 建议使用仅授权必要仓库的部署密钥（Deploy Key），不要使用个人主密钥。

### 2.3 管理员：SSH 主机信任记录

入口：仅管理员可见（后台管理或下载管理页的管理员页签）。普通用户不要展示该入口。

- 列表：`GET .../ssh-host-key?connectAddress=`（`connectAddress` 可选，按主机过滤）；
- 字段：`connectAddress`（`host` 或 `host:port`）、`keyType`、`fingerprint`、`publicKey`、
  首次信任用户 `uid`、`createAt`；
- 删除：`DELETE .../ssh-host-key?id=`，二次确认；
- 典型用途：**主机密钥变更导致克隆失败**时，删除该主机旧记录，下一次克隆会重新「首次信任」；
- 非管理员调用返回信封 `code=403`（HTTP 仍为 200），前端遇到 403 时提示无权限并隐藏入口。

### 2.4 后台配置面板 template 组件（本次新增）

后端已在 `config-properties.json` 声明菜单：

```json
{
  "name": "download-ssh",
  "title": "SSH 凭据管理",
  "nodes": [{ "nodes": [{
    "name": "download-ssh-credential-manager",
    "title": "SSH 凭据管理",
    "inputType": "template",
    "template": "download-ssh-credential-manager",
    "params": { "...": "见下表" }
  }]}]
}
```

**前端要求**：按现有 template 组件规范注册一个名为 `download-ssh-credential-manager` 的 Vue 组件
（与 `llm-provider-manager`、`proxy-config` 等现有模板组件同一注册机制），组件从 `params` 读取配置。

**`params` 字段说明**

| 字段 | 类型 | 说明 |
|---|---|---|
| `credentialApi` | string | 凭据接口基址（已含网关前缀） |
| `hostKeyApi` | string | 主机信任记录接口基址 |
| `authTypes` | string[] | 支持的认证方式：`PRIVATE_KEY` / `PASSWORD` |
| `defaultAuthType` | string | 新增表单默认认证方式 |
| `supportsUpdate` | boolean | 恒为 `false`：无编辑接口，只能删除后新增 |
| `hostKeyAdminOnly` | boolean | 恒为 `true`：主机信任记录仅管理员可见/可操作 |
| `sections` | object[] | 页签定义：`{key, title, adminOnly}`，见下 |
| `doc` | string | 本文档相对仓库路径，便于组件内展示「查看文档」链接 |

`sections` 固定两项：

1. `credentials`「我的 SSH 凭据」：**所有能进入该面板的用户**都可操作（当前登录用户自己的凭据）；
2. `hostKeys`「SSH 主机信任记录」：仅管理员可操作；非管理员调用后端会返回 403，组件应捕获并展示
   「仅管理员可管理 SSH 主机信任记录」，或直接按 `hostKeyAdminOnly` + 前端登录信息隐藏该页签。

**组件行为清单**

- 进入页面即并行拉取两个列表（凭据 + 主机信任记录）；
- 「我的 SSH 凭据」页签：列表 + 新增（弹窗表单，同 §2.2）+ 删除；
- 「SSH 主机信任记录」页签：列表 + 按主机过滤 + 删除；
- 空态、加载态、错误态（展示后端 `msg`）；
- 不要把私钥/密码回显到页面；不要把凭据内容写入前端持久化存储。

> 配置面板本身是管理员后台，因此「我的 SSH 凭据」操作的是**当前登录管理员本人**的凭据。
> 普通用户管理自己的凭据，请在下载页使用 §2.2 的入口（建议前端把该管理界面封装为可复用组件，
> 同时供下载页与后台模板组件使用）。

---

## 三、接口契约

### 3.1 通用约定

| 项 | 约定 |
|---|---|
| 网关前缀 | 内部路径 `/api/task/download/**`，经网关为 `/api/service/sfc-download-service/task/download/**` |
| 鉴权 | 走网关正常登录态（网关注入 `X-Sfc-Uid`）；未登录返回信封 `code=401` |
| 响应信封 | `{code, businessCode, msg, data}`；**业务结果以信封 `code` 为准**，业务失败时 HTTP 可能仍是 200 |
| 数值/ID | 本服务把 `Long` 统一序列化为**字符串**（`id`、`uid` 等），前端不要用 `=== 0` 这类严格比较 |
| 时间 | `createAt` 为 ISO-8601 字符串（如 `2026-10-28T12:14:51.74Z`） |
| 错误展示 | 直接展示后端 `msg`；不要写 `if (response.status === 400)` 这类判断 |

### 3.2 SSH 凭据接口（登录用户）

#### 3.2.1 列表 `GET /api/task/download/ssh-credential`

响应 `data`：`SshCredentialVO[]`（可能为空数组）

```json
{
  "code": 200,
  "msg": "OK",
  "data": [
    {
      "id": "1312924820559626240",
      "name": "GitHub 部署密钥",
      "authType": "PRIVATE_KEY",
      "username": "git",
      "fingerprint": "SHA256:KIUPIohFOmcCTwJOlmndOBdfuaPV8Nm/SmYo0IUOgKY",
      "createAt": "2026-10-28T12:55:35.825Z"
    }
  ]
}
```

#### 3.2.2 新增 `POST /api/task/download/ssh-credential`

请求体：

```jsonc
// 私钥方式
{
  "name": "GitHub 部署密钥",
  "authType": "PRIVATE_KEY",
  "username": "git",                  // 可选；地址未带用户名时必填
  "privateKey": "-----BEGIN OPENSSH PRIVATE KEY-----\n...\n-----END OPENSSH PRIVATE KEY-----",
  "passphrase": "可选：加密私钥口令"
}
// 密码方式
{
  "name": "内网 Git 服务器",
  "authType": "PASSWORD",
  "username": "git",
  "password": "..."
}
```

响应 `data`：新增后的 `SshCredentialVO`（同 3.2.1 列表项）。
服务端保存时会真实解析私钥（加密私钥用口令解密）并计算指纹；解析失败返回 400。

#### 3.2.3 删除 `DELETE /api/task/download/ssh-credential?id=`

- 成功：`code=200`，`data=null`；
- 凭据不存在或不属于当前用户：`code=400`，`msg=无效的 SSH 凭据: {id}`。

### 3.3 SSH 主机信任记录接口（管理员）

#### 3.3.1 列表 `GET /api/task/download/ssh-host-key?connectAddress=`

- `connectAddress` 可选（`host` 或 `host:port`，如 `github.com`、`git.example.com:2222`）；
- 响应 `data`：`SshHostKeyVO[]`：

```json
{
  "code": 200,
  "msg": "OK",
  "data": [
    {
      "id": "1",
      "connectAddress": "github.com",
      "keyType": "ssh-ed25519",
      "publicKey": "ssh-ed25519 AAAAC3Nza...",
      "fingerprint": "SHA256:xxxx",
      "uid": "1",
      "createAt": "2026-10-28T12:14:51.74Z"
    }
  ]
}
```

#### 3.3.2 删除 `DELETE /api/task/download/ssh-host-key?id=`

- 成功：`code=200`；
- 记录不存在：`code=404`，`msg=SSH 主机信任记录不存在: {id}`；
- 非管理员：`code=403`，`msg=仅管理员可管理 SSH 主机信任记录`（HTTP 仍为 200）。

### 3.4 创建任务接口变更（`POST /api/task/download`）

| 字段 | 必填 | 说明 |
|---|---|---|
| `sshCredentialId` | 条件必填 | **顶层字段**（不是 `options` 键）。仅 `type=GIT` 且地址为 SSH 时使用：SSH 地址**必填**；非 SSH 地址携带 → 400；凭据必须存在且属于当前创建者 |
| `proxy` | 否 | 代理**名称**；`GIT` 现已生效（HTTP(S) 与 SSH 克隆均支持 HTTP/SOCKS）；`git://` 地址带代理 → 400 |

SSH 地址的 `url` 支持：

- `ssh://[user@]host[:port]/path`；
- SCP 风格 `user@host:path`。

### 3.5 代理列表 `GET /api/task/download/proxy`

沿用原接口，返回当前用户可见的代理节点（公共代理 + 本人代理；受保护公共代理仅管理员可见）。
创建 Git 任务时把选中的代理**名称**放入 `proxy` 字段即可。

### 3.6 能力声明与类型推断

- `GET /api/task/download/capabilities`：`GIT` 的 `options` **不变**
  （`gitBranch` / `gitDepth` / `gitSubmodules` / `stripGitDir` / `gitUsername` / `gitToken`）；
  SSH 凭据是**顶层字段**，不出现在 `options` 中，前端需单独渲染下拉。
- `GET /api/task/download/detect-type?url=`：SSH 地址返回 `"GIT"`（与改造前一致，改造前只是创建任务时被拒绝）。

---

## 四、错误码与文案

| 场景 | HTTP | 信封 `code` | 典型 `msg` |
|---|---|---|---|
| 凭据/主机记录接口未登录 | 200 | 401 | `请先登录` |
| 非管理员访问主机信任记录接口 | 200 | 403 | `仅管理员可管理 SSH 主机信任记录` |
| SSH 地址未提供 `sshCredentialId` | 200 | 400 | `SSH 地址必须选择 SSH 凭据（sshCredentialId）` |
| 非 SSH 地址携带 `sshCredentialId` | 200 | 400 | `仅 SSH 地址支持选择 SSH 凭据` |
| 非 `GIT` 类型携带 `sshCredentialId` | 200 | 400 | `仅 Git SSH 克隆支持选择 SSH 凭据` |
| 凭据不存在 / 不属于当前用户 | 200 | 400 | `无效的 SSH 凭据: {id}` |
| 私钥认证且地址/凭据都没有用户名 | 200 | 400 | `SSH 地址未携带用户名，请在 SSH 凭据中配置用户名` |
| `git://` 地址带代理 | 200 | 400 | `git:// 协议不支持代理，请改用 http(s) 或 ssh 地址` |
| SSH Git 地址格式非法 | 200 | 400 | `无效的 SSH Git 仓库地址: <原文>` |
| 私钥无法解析 / 口令错误 | 200 | 400 | `SSH 私钥解析失败（私钥口令可能不正确）: <原因>` |
| 密码方式缺用户名/密码 | 200 | 400 | `SSH 密码认证需要提供用户名` / `SSH 密码不能为空` |
| 新增凭据缺 `name`/`authType`、字段超长 | **422** | 422 | `name: SSH 凭据名称不能为空` 等 |
| 请求体无法反序列化（非法枚举等） | **400** | 400 | `请求体格式错误: ...` |
| 删除不存在的信任记录 | 200 | 404 | `SSH 主机信任记录不存在: {id}` |

**执行期失败**（创建成功后才出现，展示在任务详情/失败原因中）：

| 场景 | 任务失败信息 |
|---|---|
| 凭据已被删除 | `SSH 凭据不存在或已被删除: {id}` |
| 主机密钥与已信任记录不一致 | `Git 克隆失败（SSH 主机密钥校验未通过）: ... 请管理员在「SSH 主机信任记录」中删除该主机的旧记录后重试` |
| SSH 认证失败 | `Git 克隆失败（SSH 认证失败）: ... 请检查所选 SSH 凭据是否正确...` |
| 代理不可用 | `Git 克隆失败: ...`（连接/代理相关原因） |

---

## 五、状态与边界（前端需知）

1. **无编辑接口**：凭据只能新增/删除；修改 = 删除后重新新增。
2. **凭据删除的影响**：已创建但未执行的 SSH 克隆任务会失败（执行期按 id 读取）；正在执行的任务不受影响。
3. **凭据只在当前服务实例生效**：本服务使用独立 SQLite，多实例部署时凭据/主机信任记录不跨实例共享
   （与下载任务数据一致的既有架构限制）。
4. **凭据内容不回显**：列表/详情只有元信息与指纹；前端不要尝试从任务详情、日志中获取私钥。
5. **任务列表不返回 `sshCredentialId`**：列表项字段不变；需要排查时看任务详情参数（后端不提供明文密钥）。
6. **不要内嵌凭证到 URL**：如 `https://user:token@host/...` 或 SSH 密码形式。虽然日志已脱敏，
   但任务地址会原样存储并出现在任务列表中，建议用凭据/令牌字段代替。
7. **子模块**：开启 `gitSubmodules` 时，任务级代理与 SSH 凭据会传递给子模块；子模块地址仅允许
   `http`/`https`/`ssh`/`git`；HTTP(S) 父仓库 + SSH 子模块不在支持范围（任务会失败，属预期）。
8. **主机密钥是 TOFU**：首次连接未知主机自动信任并全局记录；指纹变化会导致克隆失败，
   需管理员删除旧记录。前端在失败原因中看到上述提示时，引导管理员到 §2.3 处理。

---

## 六、联调与自测清单

### 6.1 前置

- `sfc-core`、`sfc-download-service`（8092）、`sfc-gateway` 已启动；
- 下载服务与 core 使用同一份 `SFC_INTERNAL_KEY`；
- 登录后访问（网关路径）。

### 6.2 自测用例

| # | 步骤 | 预期 |
|---|---|---|
| 1 | 打开创建任务表单，类型选 `GIT`，地址填 `git@github.com:owner/repo.git` | 出现「SSH 凭据」下拉且必填；代理下拉可用 |
| 2 | 凭据下拉为空时点「新增凭据」，提交私钥方式 | 新增成功，列表出现该凭据（含 `fingerprint`），下拉自动选中 |
| 3 | 用该凭据创建 SSH 克隆任务 | 任务创建成功（`code=200`），执行日志显示「SSH凭据=...」 |
| 4 | 地址改为 `ssh://host/owner/repo.git`（不带用户名）并选未配置用户名的凭据 | 创建返回 400「SSH 地址未携带用户名，请在 SSH 凭据中配置用户名」 |
| 5 | 地址改为 HTTPS + 选代理创建任务 | 任务日志显示「代理=...」，克隆走代理（行为变化，旧前端需回归） |
| 6 | 地址改为 `git://host/repo.git` + 选代理 | 创建返回 400「git:// 协议不支持代理...」 |
| 7 | 用他人凭据 id 创建任务 | 返回 400「无效的 SSH 凭据: {id}」 |
| 8 | 普通用户调用 `GET /ssh-host-key` | 信封 `code=403`，前端提示无权限 |
| 9 | 管理员打开信任记录列表 | 能看到首次 SSH 克隆产生的主机记录；删除后再次克隆会重新信任 |
| 10 | 后台「插件配置 → 离线下载 → SSH 凭据管理」 | 出现新菜单，`download-ssh-credential-manager` 组件正常渲染两个页签 |
| 11 | 新增密码方式凭据（正确/错误密码分别创建任务） | 正确密码克隆成功；错误密码任务失败且失败原因可读 |

### 6.3 配置面板验证（后端侧参考）

`GET /api/meta/pluginInfo`（服务内部接口，core 后台调用）返回的 `groups` 中应包含
`download-ssh` 菜单，其模板节点为：

```json
{
  "name": "download-ssh-credential-manager",
  "title": "SSH 凭据管理",
  "inputType": "template",
  "template": "download-ssh-credential-manager",
  "params": { "credentialApi": "...", "hostKeyApi": "...", "sections": ["..."] }
}
```

---

## 七、FAQ

**Q1：为什么 SSH 地址必须选凭据？**
本服务不读取部署机的 `~/.ssh`（不借用服务器身份），SSH 认证完全由用户凭据驱动；
没有凭据的 SSH 克隆必然失败，因此创建期直接拒绝，避免产生必然失败的任务。

**Q2：为什么没有「编辑凭据」？**
凭据包含密钥内容，接口刻意只提供新增/删除，避免把历史密钥回显到前端。修改请删除后重新新增。

**Q3：克隆失败提示主机密钥不一致怎么办？**
管理员进入「SSH 主机信任记录」，按 `connectAddress` 过滤后删除旧记录，重新执行克隆即可
（首次信任会重新记录当前主机密钥）。

**Q4：以前给 Git 任务传代理没生效，现在会怎样？**
现在会真正走代理。若旧前端在 Git 任务上默认带了一个无效代理名称，创建任务会因代理解析失败返回 400；
请检查默认值。

**Q5：凭据存储安全吗？**
凭据以明文存放在下载服务本地数据库（与 core 现有 aria2 secret 等做法一致），接口不回显、日志已脱敏；
建议限制 `download.db` 文件权限，并使用仅授权必要仓库的部署密钥。

**Q6：能力声明里为什么看不到 SSH 凭据选项？**
`sshCredentialId` 是**顶层字段**而不是 `options` 键，`capabilities` 的 `GIT.options` 保持不变；
前端需要按 §2.1 自行渲染「SSH 凭据」下拉。
