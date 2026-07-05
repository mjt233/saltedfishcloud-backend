# OIDC 服务端支持设计

## 1. 背景

当前系统已经具备开放平台 OAuth 能力，但令牌语义并非标准 OAuth2/OIDC：

1. 客户端先通过授权码 `code` 换取长期 `Access Token`
2. 该 `Access Token` 在现有实现里更接近标准语义中的 `refresh_token`
3. 客户端再使用该长期令牌换取短期 `ApiTicket`
4. 开放平台接口最终使用 `ApiTicket` 作为 Bearer 凭证

现阶段需要在**不破坏现有开放平台客户端**的前提下，为服务端补齐 OIDC Provider 能力，并尽可能复用 Spring Security 体系实现标准协议能力。

## 2. 目标与约束

### 2.1 目标

1. 为当前服务端新增标准 OIDC Provider 能力，至少覆盖 discovery、JWKS、authorize、token、userinfo。
2. 尽可能基于 Spring Security 与 Spring Authorization Server 实现协议层能力，而不是手写整套 OIDC 协议栈。
3. 保留现有双层令牌内核：OIDC `access_token` 映射现有短期 `ApiTicket`，OIDC `refresh_token` 映射现有长期 `Access Token`。
4. 同时支持 confidential client 与 public client，并对 public client 支持 PKCE。
5. 保持现有 `/api/oauth/**` 与 `/api/openApi/**` 旧接口继续可用。

### 2.2 非目标

1. 本阶段不移除既有开放平台 OAuth 接口。
2. 本阶段不重写既有开放平台接口权限模型，仍延续 `profile`、`storage_read`、`storage_write` 等 scope 语义。
3. 本阶段不单独设计第二套完全独立的客户端、授权和票据体系。

## 3. 核心决策

### 3.1 总体策略

采用**协议映射层 + Spring Authorization Server 骨架**方案：

1. Spring Authorization Server 负责标准 OIDC/OAuth2 端点、过滤器链、客户端认证、PKCE、错误响应和 discovery/JWKS 能力。
2. 现有开放平台授权、长期令牌和 ApiTicket 服务继续保留，作为底层事实来源与令牌语义来源。
3. 通过 Spring Security 扩展点完成协议映射，而不是新写一套平行协议实现。

### 3.2 令牌语义映射

| OIDC/OAuth2 标准语义 | 系统内部映射 |
|---|---|
| `authorization_code` | 现有授权确认流程生成的授权上下文 |
| `access_token` | 现有短期 `ApiTicket` |
| `refresh_token` | 现有长期 `Access Token` |
| `id_token` | 新增 OIDC 身份令牌 |

该决策保证：

1. 新 OIDC 客户端可以直接按标准协议接入。
2. 旧客户端仍可继续使用 `code -> Access Token -> ApiTicket` 旧流程。
3. 开放平台接口不需要改为接受另一套全新访问凭证。

### 3.3 Spring 生态优先

实现时优先引入 **Spring Authorization Server**，并尽可能复用以下能力：

1. `AuthorizationServerSettings`
2. `RegisteredClientRepository`
3. `OAuth2AuthorizationService`
4. `OAuth2AuthorizationConsentService`
5. OIDC discovery / UserInfo / JWK Source
6. 标准 token endpoint、client authentication、PKCE 校验

自定义逻辑仅集中在：

1. 客户端元数据适配
2. 现有授权记录与 consent 桥接
3. `access_token` / `refresh_token` 与现有票据模型的映射
4. claims 与 userinfo 数据映射

## 4. 架构设计

### 4.1 总体结构

新增一组 OIDC 协议组件，挂在 Spring Security / Spring Authorization Server 过滤器链中：

1. **OIDC Server Config**
   - 注册 Authorization Server 所需 Bean
   - 配置 issuer、端点路径、JWK Source、token 生成器、client authentication
2. **OIDC Client Adapter**
   - 将现有 `ThirdPartyApp` / `ThirdPartyAppKey` 适配为 `RegisteredClient`
3. **OIDC Consent Adapter**
   - 将现有 `ThirdPartyAppAuthorization` 适配为 Authorization Consent 事实来源
4. **OIDC Token Bridge**
   - 负责将标准 token issuance 结果桥接到现有长期 `Access Token` 与短期 `ApiTicket`
5. **OIDC Claims/UserInfo Mapper**
   - 统一生成 `id_token` claims 与 `userinfo` 响应内容

### 4.2 端点设计

建议提供以下端点：

| 端点 | 作用 |
|---|---|
| `/.well-known/openid-configuration` | 暴露 OIDC 元数据 |
| `/oauth2/jwks` | 暴露 `id_token` 验签公钥 |
| `/oauth2/authorize` | 授权端点 |
| `/oauth2/token` | 令牌端点 |
| `/oauth2/userinfo` | 用户信息端点 |
| `/oauth2/revoke` | 令牌撤销端点 |
| `/oauth2/introspect` | 令牌自省端点 |
| `/connect/logout` | RP 发起登出端点 |

其中 discovery 仅声明系统真正实现的能力，不暴露未落地的协议字段。

### 4.3 现有接口兼容

以下接口继续保留：

1. `/api/oauth/authorize`
2. `/api/openApi/auth/getAccessToken/v1`
3. `/api/openApi/auth/getApiTicket/v1`

兼容策略如下：

1. 旧接口继续对旧客户端提供既有协议。
2. 新 OIDC 端点复用同一套授权、长期令牌、短期票据与撤销语义。
3. 无论客户端走新协议还是旧协议，授权撤销、应用停用、密钥失效后的行为必须一致。

## 5. 客户端模型设计

### 5.1 复用现有模型

优先基于现有表扩展，而不是再创建一套平行客户端模型：

1. `ThirdPartyApp`
2. `ThirdPartyAppKey`

### 5.2 需要补充的客户端元数据

为支持 OIDC，需要补充以下能力：

| 元数据 | 说明 |
|---|---|
| client type | public / confidential |
| redirect URI 列表 | OIDC 更适合多个合法重定向地址 |
| token endpoint auth method | `client_secret_basic`、`client_secret_post`、`none` |
| PKCE 要求 | 是否必须启用 PKCE |
| scopes / capabilities | 允许的 scope 与附加能力开关 |
| post logout redirect URI 列表 | RP 发起登出回跳地址 |

### 5.3 RegisteredClient 适配

实现 `RegisteredClientRepository` 适配层：

1. 读取 `ThirdPartyApp` 与 `ThirdPartyAppKey`
2. 生成 Spring Authorization Server 所需 `RegisteredClient`
3. 将已有应用密钥映射为 `ClientAuthenticationMethod`
4. 将 public client 映射为 `ClientAuthenticationMethod.NONE`
5. 将 PKCE 要求映射到 `ClientSettings`

这样可以避免后台管理界面和现有应用管理逻辑被整体重写。

## 6. 授权与同意设计

### 6.1 授权端点输入

`/oauth2/authorize` 首期需要支持并正确处理：

1. `response_type=code`
2. `client_id`
3. `redirect_uri`
4. `scope`
5. `state`
6. `nonce`
7. `prompt`
8. `max_age`
9. `login_hint`
10. `code_challenge`
11. `code_challenge_method`

### 6.2 scope 规则

1. OIDC 请求必须包含 `openid`
2. `profile`、`email` 等 OIDC scope 由 claims 能力决定
3. `storage_read`、`storage_write` 等开放平台业务 scope 保持既有语义
4. 最终授权结果是 OIDC scope 与开放平台业务 scope 的并集

### 6.3 同意模型桥接

用户同意结果优先复用 `ThirdPartyAppAuthorization` 作为事实来源：

1. Spring Authorization Server 的 consent 结果需要映射回该记录
2. 旧接口授权和新 OIDC 授权应共享相同授权结果
3. 撤销授权时，应同时影响新旧两套入口

### 6.4 PKCE 规则

1. public client 必须启用 PKCE
2. confidential client 默认允许使用 PKCE，建议后台支持配置为必需
3. 首期仅支持 `S256`
4. 授权码交换时必须校验 `code_verifier`

## 7. 令牌与密钥设计

### 7.1 `id_token`

`id_token` 为新增标准身份令牌，使用 Spring Authorization Server OIDC 能力签发。

建议：

1. 使用独立的非对称密钥对签名
2. 暴露对应 JWKS
3. 支持 `kid` 轮换
4. 不复用当前全局 HS256 `JwtUtils`

原因是当前 `JwtUtils` 仅适合服务端内部验证，不适合 OIDC 客户端通过 JWKS 做标准验签。

### 7.2 `access_token`

OIDC `access_token` 直接映射现有短期 `ApiTicket`：

1. 仍保持 15 分钟有效期
2. 可直接访问现有开放平台接口
3. 在 Spring Authorization Server 看来它是标准 bearer token
4. 在系统内部仍由既有 `ThirdPartyAppApiTicketService` 负责签发与校验

### 7.3 `refresh_token`

OIDC `refresh_token` 直接映射现有长期 `Access Token`：

1. 复用现有长期令牌持久化与校验逻辑
2. `refresh_token` grant 时据此重新签发新的 `access_token(ApiTicket)` 与 `id_token`
3. 用户撤销授权后必须立即失效

### 7.4 Token 端点返回

`/oauth2/token` 成功响应中应返回：

1. `access_token`
2. `token_type`
3. `expires_in`
4. `refresh_token`
5. `id_token`
6. `scope`

### 7.5 令牌失效与撤销

以下场景必须统一处理：

1. 用户主动撤销授权
2. 第三方应用被停用
3. 第三方应用密钥失效
4. 新的同类型令牌替换旧令牌

统一要求：

1. 旧开放平台路径看到的令牌状态与新 OIDC 路径一致
2. 撤销一个授权应同时影响长期令牌、短期 ApiTicket、userinfo、自省结果

## 8. Claims 与 UserInfo 设计

### 8.1 `sub`

`sub` 使用系统用户 `uid` 的稳定字符串值。

这样可以保证：

1. 同一用户在同一发行者下身份稳定
2. 现有开放平台接口与授权记录更容易做映射

### 8.2 `id_token` claims

最少包含：

1. `iss`
2. `sub`
3. `aud`
4. `exp`
5. `iat`
6. `auth_time`
7. `nonce`（请求携带时写回）

可选补充：

1. `azp`
2. `sid`
3. `at_hash`

### 8.3 `userinfo` claims

按 scope 返回：

| scope | claims |
|---|---|
| `openid` | `sub` |
| `profile` | `preferred_username`、`name`、`picture` |
| `email` | `email`、`email_verified` |

声明映射应集中在单独的 mapper/service 中，不应分散在 controller 内手工拼装。

## 9. 错误处理设计

### 9.1 新端点

新 OIDC/OAuth2 端点统一返回标准错误语义，例如：

1. `invalid_request`
2. `invalid_client`
3. `invalid_grant`
4. `unauthorized_client`
5. `invalid_scope`
6. `access_denied`
7. `unsupported_grant_type`

### 9.2 旧端点

旧开放平台接口继续保留当前 `businessCode` 语义，不强行改协议返回格式。

### 9.3 协议并行原则

协议并行时的原则是：

1. 标准端点对标准客户端友好
2. 旧端点对存量客户端无破坏
3. 两端共享同一套核心授权与票据失效事实来源

## 10. 模块边界建议

建议拆为以下实现单元：

1. **Authorization Server 配置单元**
   - Spring Security / Spring Authorization Server Bean 与过滤器链配置
2. **Client Adapter 单元**
   - `ThirdPartyApp` / `ThirdPartyAppKey` 到 `RegisteredClient` 的映射
3. **Consent / Authorization Bridge 单元**
   - 与 `ThirdPartyAppAuthorization`、现有授权码逻辑桥接
4. **Token Bridge 单元**
   - `access_token` / `refresh_token` 与现有服务桥接
5. **JWK / ID Token 单元**
   - 密钥管理、`id_token` 签发、JWKS 发布
6. **Claims / UserInfo 单元**
   - 标准 claims 与用户信息响应映射
7. **Legacy Compatibility 单元**
   - 旧开放平台接口复用新公共逻辑

## 11. 验收标准

### 11.1 协议验收

1. 标准 OIDC 客户端可以正确读取 discovery。
2. 客户端可以通过 JWKS 验证 `id_token`。
3. confidential client 可完成 `authorize -> token -> userinfo -> refresh_token` 全链路。
4. public client 可完成 `authorize(PKCE) -> token -> userinfo -> refresh_token` 全链路。

### 11.2 资源访问验收

1. OIDC `access_token` 可以直接访问现有开放平台接口。
2. scope 与现有 `SCOPE_*` 权限映射保持一致。
3. 旧 `ApiTicket` 鉴权过滤器行为不被破坏。

### 11.3 一致性验收

1. 用户撤销授权后，新旧协议下的令牌都立即失效。
2. 应用被停用后，既有令牌与新签发流程都被阻断。
3. 旧客户端继续可用。

## 12. 实施建议

实现阶段建议继续遵循以下顺序：

1. 先接入 Spring Authorization Server 与最小 OIDC Server 配置
2. 再完成 `RegisteredClient` 与 consent 适配
3. 然后实现令牌桥接，使 `access_token` / `refresh_token` 与现有体系打通
4. 最后补齐 userinfo、revocation、introspection、logout 与兼容回归

该顺序可以在尽快建立协议骨架的同时，把风险最大的令牌语义映射放在可控范围内逐步收敛。
