# OIDC Provider 支持

咸鱼云网盘提供基于 Spring Authorization Server 的标准 OIDC / OAuth 2.1 端点，支持标准 OIDC 客户端直接接入。

## 已实现能力

当前版本已实现以下标准端点：

| 端点 | 说明 |
|------|------|
| `/.well-known/openid-configuration` | OIDC Discovery 元数据 |
| `/oauth2/jwks` | `id_token` 验签公钥 |
| `/oauth2/authorize` | 标准授权端点 |
| `/oauth2/device_authorization` | 设备授权端点 |
| `/oauth/device` | 设备激活页（供用户输入/确认 `user_code`） |
| `/oauth2/device_verification` | 设备核验端点 |
| `/oauth2/token` | 标准令牌端点 |
| `/oauth2/userinfo` | 标准 UserInfo 端点 |
| `/oauth2/revoke` | 标准令牌撤销端点 |
| `/oauth2/introspect` | 标准令牌自省端点 |

> 当前版本**不建议**将 `/connect/logout` 作为稳定的 RP-Initiated Logout 能力对外依赖；如需让 OIDC 令牌立即失效，请优先使用 `/oauth2/revoke` 或现有授权撤销能力。

## 令牌说明

标准 OAuth 2.1 / OIDC 返回的令牌：

| 令牌类型 | 说明 |
|------|------|
| `access_token` | 短期访问令牌，用于调用 API |
| `refresh_token` | 长期刷新令牌，用于获取新的 access_token |
| `id_token` | OIDC 身份令牌，包含用户身份信息（JWK/JWKS 验签） |

调用 API 时使用标准 Bearer 认证：

```
Authorization: Bearer {access_token}
```

## 客户端类型

当前支持两类客户端：

| 类型 | 说明 |
|------|------|
| confidential client | 支持 `client_secret_basic` / `client_secret_post` |
| public client | 使用 `none`，并要求 PKCE |

说明：

1. public client 必须启用 PKCE。
2. confidential client 可以使用 PKCE。
3. 客户端元数据由第三方应用管理能力维护。

## 支持的 scope 与 UserInfo claims

当前版本支持以下 scope：

| scope | UserInfo claims / 权限语义 |
|------|------|
| `openid` | `sub` |
| `profile` | `preferred_username`、`name`、`picture` |
| `email` | `email`、`email_verified` |
| `storage_read` | 开放平台存储读权限 |
| `storage_write` | 开放平台存储写权限 |

当前 `UserInfo` 声明规则：

1. `sub` 固定为系统用户 `uid` 的字符串形式。
2. `preferred_username` / `name` 使用系统用户名。
3. `picture` 沿用现有开放平台头像地址格式：`{issuer}/api/user/avatar/{username}?uid={uid}`。
4. 当前系统模型中没有持久化的邮箱验证标志，因此 `email_verified` 当前固定返回 `false`。

## 标准授权流程

### confidential client

```text
GET  /oauth2/authorize
POST /oauth2/token              -> access_token + refresh_token + id_token
GET  /oauth2/userinfo           -> Bearer access_token
POST /oauth2/token(grant_type=refresh_token)
```

### public client（PKCE）

```text
GET  /oauth2/authorize?code_challenge=...&code_challenge_method=S256
POST /oauth2/token              -> 携带 code_verifier
GET  /oauth2/userinfo
POST /oauth2/token(grant_type=refresh_token)
```

### device authorization grant

```text
POST /oauth2/device_authorization
GET  /oauth/device                    -> 用户输入或确认 user_code
GET  /oauth2/device_verification      -> 未登录时跳转 /oauth
GET  /oauth2/device-consent           -> 已登录后确认 scope
POST /oauth2/device_verification      -> 完成授权
POST /oauth2/token(grant_type=urn:ietf:params:oauth:grant-type:device_code)
```

说明：

1. `verification_uri` 会返回系统自带的 `/oauth/device` 页面，而不是要求依赖外部前端资源。
2. 设备授权完成后的 `access_token` / `refresh_token` 语义与授权码模式完全一致。
3. 若客户端是 public client，后续 refresh_token 使用方式与标准 OIDC 流程一致。

## 配置项

启用标准 OIDC 能力需要开启 `sys.oidc.enabled=true`，并至少正确配置 `sys.oidc.issuer`。

当前可配置的主要端点包括：

| 配置项 | 默认值 |
|------|------|
| `sys.oidc.authorization-endpoint` | `/oauth2/authorize` |
| `sys.oidc.token-endpoint` | `/oauth2/token` |
| `sys.oidc.device-authorization-endpoint` | `/oauth2/device_authorization` |
| `sys.oidc.device-verification-endpoint` | `/oauth2/device_verification` |
| `sys.oidc.user-info-endpoint` | `/oauth2/userinfo` |
| `sys.oidc.jwk-set-endpoint` | `/oauth2/jwks` |
| `sys.oidc.revocation-endpoint` | `/oauth2/revoke` |
| `sys.oidc.introspection-endpoint` | `/oauth2/introspect` |

## 调用建议

1. 标准 OIDC 客户端优先使用 discovery 自动发现端点。
2. 若应用已不再需要访问权限，请使用 `/oauth2/revoke` 现有授权撤销能力，避免长期 refresh token 继续有效。
