# ThirdPartyApp ApiTicket 重构设计

## 背景

当前 `ThirdPartyAppTokenService` 同时承担了两类职责：

1. OAuth 授权码与 Access Token 管理
2. ApiTicket 的签发、校验、撤销与查询

同时，`ThirdPartyAppToken` 上还持久化了 `apiTicket` 字段，导致 ApiTicket 生命周期与 Access Token 记录耦合在一起。

本次重构目标是将这两类职责拆开，同时尽量保持现有 HTTP 接口行为兼容。

## 目标

1. 将 ApiTicket 生命周期管理迁移到 `ThirdPartyAppApiTicketService`
2. 保证 `ThirdPartyAppApiTicketService` 不依赖 `ThirdPartyAppTokenService`
3. 从 `ThirdPartyAppToken` 中移除 `apiTicket` 字段
4. 保持现有 HTTP 接口继续可用
5. 保留 `getExistingApiTicket()` 对重构后新签发永久票据的支持能力

## 非目标

1. 不迁移历史 `ThirdPartyAppToken.apiTicket` 数据
2. 不兼容升级前旧 ApiTicket 的查询与校验
3. 不保留历史 ApiTicket 的兼容撤销逻辑

## 总体设计

### 职责拆分

#### `ThirdPartyAppTokenService`

`ThirdPartyAppTokenService` 继续负责 OAuth 相关的 Token 流程：

- 授权码签发
- Access Token 签发
- ApiTicket 申请前所需的 Access Token 校验
- 应用级 revoke 总入口

它可以继续保留 `getApiTicket(...)` 作为“申请入口”，因为当前 app、授权、Access Token 的上下文都已经聚合在这里。但它不再自己执行 ApiTicket 的 JWT 生成、持久化、校验和撤销。

它在 ApiTicket 相关流程中的职责收敛为：

1. 校验 `appId`、`uid`、`accessToken`
2. 读取授权 scope
3. 判断是否允许申请永久 ApiTicket
4. 组装 `ThirdPartyAppApiTicketPayload`
5. 将真正的签发与撤销工作委托给 `ThirdPartyAppApiTicketService`

#### `ThirdPartyAppApiTicketService`

`ThirdPartyAppApiTicketService` 成为 ApiTicket 生命周期的唯一负责人，并且只聚焦 ApiTicket 本身，不承担 app 合法性、授权信息、Access Token 合法性的业务判断。

它负责：

1. 基于已校验参数签发 ApiTicket JWT
2. 持久化 ApiTicket 记录
3. 按 app/user/type/jti 撤销票据
4. 基于持久化记录校验 ApiTicket
5. 查询当前用户当前应用最新有效的永久 ApiTicket

`ThirdPartyAppApiTicketService` 必须不依赖 `ThirdPartyAppTokenService`。

## 数据模型调整

### `ThirdPartyAppToken`

移除字段：

- `apiTicket`

调整后，`ThirdPartyAppToken` 只表达 Access Token 状态。

### `ThirdPartyAppApiTicket`

新增一个用于持久化票据原文的字段：

- `apiTicket`（`@Column(length = 1024)`）

继续保留以下字段：

- `appId`
- `uid`
- `jti`
- `permanent`
- `expiredDate`
- `revoked`

这样 `ThirdPartyAppApiTicket` 就同时承载“票据原文”和“票据状态”，也能为 `getExistingApiTicket()` 提供稳定的数据来源。

## 服务接口调整

### `ThirdPartyAppTokenService`

计划调整：

1. 移除 `parseAndValidateApiTicket(String apiTicket)`
2. 保留 `getApiTicket(...)`，但语义变为“签发前校验与委托入口”
3. 保留 `revoke(Long appId, Long uid)` 作为统一清理入口

### `ThirdPartyAppApiTicketService`

在现有记录管理方法基础上，补齐 ApiTicket 生命周期方法。

目标接口形态：

1. `String issue(ThirdPartyAppApiTicketPayload payload, boolean revokeOlder)`
2. `ThirdPartyAppApiTicketPayload parseAndValidateApiTicket(String apiTicket)`
3. `Optional<ThirdPartyAppApiTicket> findLatestActivePermanentTicket(Long appId, Long uid)`
4. 现有 `findByJti(...)`
5. 现有 `revokeByAppIdAndUid(...)`
6. 现有 `revokeByAppIdAndUidAndPermanent(...)`
7. 现有 `revokeByJti(...)`

方法命名实现时可以按项目风格微调，但职责边界保持不变。

## 请求与校验流程

### ApiTicket 签发流程

1. 调用方进入 `ThirdPartyAppTokenService#getApiTicket(...)`
2. `ThirdPartyAppTokenService` 负责校验：
   - Access Token 存在且未过期
   - Access Token 与用户、应用匹配
   - 用户授权存在
   - 当申请永久票据时，应用具备对应能力
3. `ThirdPartyAppTokenService` 组装 `ThirdPartyAppApiTicketPayload`
4. `ThirdPartyAppTokenService` 调用 `ThirdPartyAppApiTicketService.issue(payload, revokeOlder)`
5. `ThirdPartyAppApiTicketService` 内部负责：
   - 按需撤销旧同类票据
   - 生成 JWT
   - 计算票据过期信息
   - 持久化新的 `ThirdPartyAppApiTicket` 记录（包含票据原文）
   - 返回 JWT 原文

### ApiTicket 校验流程

1. 调用方进入 `ThirdPartyAppApiTicketService.parseAndValidateApiTicket(apiTicket)`
2. 服务解析 JWT payload
3. 根据 `jti` 查询持久化记录
4. 校验以下条件：
   - 记录存在
   - 记录未撤销
   - 记录未过期
   - 记录中的 `appId`、`uid`、`permanent` 与 payload 一致
5. 校验通过后返回 payload
6. 任一条件不满足时统一抛出 `JsonException(OAuthError.INVALID_TOKEN)`

本次重构只保证重构后新签发的 ApiTicket 能通过该流程。升级前旧票据因为没有新表记录，允许直接校验失败。

### 撤销流程

`ThirdPartyAppTokenService#revoke(appId, uid)` 仍然保留为顶层 revoke 操作，但内部行为调整为：

1. 调用 `ThirdPartyAppApiTicketService.revokeByAppIdAndUid(appId, uid)` 撤销该用户该应用下的 ApiTicket
2. 删除 Access Token 记录
3. 删除授权记录

`ThirdPartyAppTokenService` 不再自己解析或直接失效单个 ApiTicket 原文。

## 控制器与安全层调整

### `OpenApiAuthController`

HTTP 接口形态保持不变，内部仍可通过 `ThirdPartyAppTokenService#getApiTicket(...)` 完成申请流程。

### `JwtOpenApiTicketFilter`

将依赖从 `ThirdPartyAppTokenService` 切换为 `ThirdPartyAppApiTicketService`，票据校验直接由新服务完成。

### `McpOAuthController`

针对永久 ApiTicket 查询流程：

1. 通过 `ThirdPartyAppApiTicketService` 查询最新有效永久票据
2. 对查询出的票据原文做有效性校验
3. 返回遮掩后的票据字符串

`getExistingApiTicket()` 仅保证对本次重构后新签发的永久票据有效。

## 异常处理

### `ThirdPartyAppTokenService`

继续沿用现有 `JsonException` 与 `OAuthError` 机制处理以下错误：

- Access Token 非法
- 授权不存在
- 不允许申请永久 ApiTicket

### `ThirdPartyAppApiTicketService`

以下情况统一抛出 `INVALID_TOKEN`：

- JWT 格式非法
- 找不到持久化记录
- 记录已撤销
- 记录已过期
- payload 与持久化记录不一致

撤销相关接口继续保持幂等：

- 批量撤销返回影响行数
- 按 jti 撤销返回是否命中持久化记录

## 兼容性说明

### 保留兼容

1. 现有 HTTP 路由与参数
2. MCP 侧申请永久 ApiTicket 的现有使用方式

### 不保留兼容

1. 历史 `ThirdPartyAppToken.apiTicket` 数据
2. 升级前旧 ApiTicket 的查询兼容
3. 升级前旧 ApiTicket 的校验兼容
4. 升级前旧 ApiTicket 的撤销兼容

## 验收标准

1. `ThirdPartyAppToken` 不再包含 `apiTicket`
2. `ThirdPartyAppApiTicketService` 负责 ApiTicket 的签发、校验、撤销与永久票据查询
3. `ThirdPartyAppApiTicketService` 不依赖 `ThirdPartyAppTokenService`
4. 现有 HTTP 接口继续可用
5. `getExistingApiTicket()` 对重构后新签发的永久票据继续生效
6. 升级前旧 ApiTicket 允许在升级后直接失效
