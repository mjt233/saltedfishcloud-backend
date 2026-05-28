# OIDC 授权服务器实现参考

本文档记录基于 Spring Authorization Server 的 OIDC 授权服务器内部实现细节，供开发和维护参考。

## 架构概览

```
请求进入
  │
  ▼
┌─────────────────────────────────────────────────────┐
│  SecurityFilterChain (Order=HIGHEST_PRECEDENCE)     │
│  securityMatcher: authorizationServer.getEndpointsMatcher()
│  匹配 /oauth2/**, /.well-known/**                    │
│                                                      │
│  JwtAuthenticationFilter        ← 从 Cookie 提取 JWT │
│  OAuth2AuthorizationEndpointFilter                   │
│  OAuth2TokenEndpointFilter                           │
│  OAuth2ClientAuthenticationFilter                    │
│  ...                                                 │
│  AuthorizationFilter                                 │
│  OidcLoginRedirectEntryPoint → /oauth (未登录时)      │
└─────────────────────────────────────────────────────┘
  │ (未匹配)
  ▼
┌─────────────────────────────────────────────────────┐
│  SecurityFilterChain (Order=2)                       │
│  主应用链，处理 /api/** 等                             │
└─────────────────────────────────────────────────────┘
```

## 核心类与职责

### 配置层

| 类 | 职责 |
|---|---|
| `OidcAuthorizationServerConfig` | 注册所有 OIDC Bean，配置安全过滤器链 |
| `OidcServerProperty` | `sys.oidc.*` 配置属性绑定 |
| `SecurityConfig` | 主应用安全过滤器链（Order=2） |

### 适配层（Spring Authorization Server ↔ 遗留系统）

| 类 | 职责 |
|---|---|
| `OidcRegisteredClientRepository` | `ThirdPartyApp` → `RegisteredClient` 映射 |
| `OidcAuthorizationConsentService` | `ThirdPartyAppAuthorization` → `OAuth2AuthorizationConsent` 映射 |
| `OidcAuthorizationService` | 混合式授权服务：内存委托 + 遗留 token 回退 |
| `OidcTokenBridgeService` | OIDC token 与遗留 ApiTicket/AccessToken 的桥接 |
| `OidcTokenGenerator` | 自定义 token 生成器 |
| `OidcUserClaimsMapper` | UserInfo 声明映射 |
| `JwtAuthenticationFilter` | 从 Cookie/Header/Param 提取 JWT 设置认证（两个链共用） |

## 安全过滤器链配置

### OIDC 链（最高优先级）

`OidcAuthorizationServerConfig.oidcSecurityFilterChain()`:

```java
OAuth2AuthorizationServerConfigurer authorizationServer =
        OAuth2AuthorizationServerConfigurer.authorizationServer();

http.with(authorizationServer, server -> server
                .authorizationServerSettings(settings)      // 端点路径
                .oidc(oidc -> oidc.userInfoEndpoint(...))   // UserInfo 映射
                .deviceAuthorizationEndpoint(...)           // 设备授权
                .deviceVerificationEndpoint(...))           // 设备核验
        .addFilterBefore(jwtAuthenticationFilter, AbstractPreAuthenticatedProcessingFilter.class)
        .authenticationProvider(authenticationProvider)      // 自定义 BCrypt PasswordEncoder
        .securityMatcher(authorizationServer.getEndpointsMatcher())
        .authorizeHttpRequests(anyRequest().authenticated())
        .csrf(csrf -> csrf.ignoringRequestMatchers(...))
        .exceptionHandling(ex -> ex.defaultAuthenticationEntryPointFor(
                new OidcLoginRedirectEntryPoint("/oauth"),
                new MediaTypeRequestMatcher(MediaType.TEXT_HTML)));
```

**过滤器顺序要点**：`JwtAuthenticationFilter` 必须在 `AbstractPreAuthenticatedProcessingFilter` 之前（而非 `UsernamePasswordAuthenticationFilter` 之前），否则 `OAuth2AuthorizationEndpointFilter` 执行时 SecurityContext 中还没有认证信息，导致已登录用户访问 `/oauth2/authorize` 时被当作未认证处理而返回 404。

### 客户端认证

`ClientSecretAuthenticationProvider` 使用 `SecureUtils.getBCryptPasswordEncoder()`（`BCryptPasswordEncoder`）验证 client secret，而非 Spring Authorization Server 默认的 `DelegatingPasswordEncoder`。数据库中存储的是 BCrypt 哈希，直接传入 `RegisteredClient.clientSecret()` 即可：

```java
// OidcAuthorizationServerConfig.authenticationProvider()
ClientSecretAuthenticationProvider provider = new ClientSecretAuthenticationProvider(...);
provider.setPasswordEncoder(SecureUtils.getBCryptPasswordEncoder());  // 直接使用 BCryptPasswordEncoder

// OidcRegisteredClientRepository.findByClientId()
String clientSecret = keyRepo.findByAppId(app.getId()).stream()
        .findFirst()
        .map(ThirdPartyAppKey::getClientSecretHash)   // BCrypt 哈希，无需 {bcrypt} 前缀
        .orElse(null);
```

## 授权流程

### 授权码模式（Authorization Code）

```
1. GET /oauth2/authorize?response_type=code&client_id=...&scope=...&redirect_uri=...
   │
   ├─ 未登录 → JwtAuthenticationFilter 未设置认证
   │           AuthorizationFilter 拒绝
   │           OidcLoginRedirectEntryPoint 重定向到 /oauth?client_id=...&redirect_uri=...
   │
   └─ 已登录 → JwtAuthenticationFilter 从 Cookie 提取 token 设置认证
               OAuth2AuthorizationEndpointFilter 处理授权请求
               ├─ 需要用户同意 → 重定向到 consent 页面
               └─ 已有授权或自动批准 → 重定向到 redirect_uri?code={授权码}

2. POST /oauth2/token
   Authorization: Basic base64(client_id:client_secret)
   grant_type=authorization_code&code={授权码}&redirect_uri=...
   │
   └─ OAuth2ClientAuthenticationFilter 验证客户端凭证
      OAuth2AuthorizationCodeAuthenticationProvider 验证授权码
      OidcTokenGenerator 生成 token
      返回 { access_token, refresh_token, id_token, token_type, expires_in }

3. GET /oauth2/userinfo
   Authorization: Bearer {access_token}
   │
   └─ 返回 UserInfo claims（按 scope 过滤）
```

### 设备授权模式（Device Authorization Grant）

```
1. POST /oauth2/device_authorization
   │
   └─ 返回 { device_code, user_code, verification_uri, interval, expires_in }

2. 用户访问 verification_uri (/oauth/device)
   输入 user_code → 重定向到 /oauth2/device_verification

3. /oauth2/device-consent 页面
   展示客户端名称与请求的 scopes，用户确认授权

4. POST /oauth2/token
   grant_type=urn:ietf:params:oauth:grant-type:device_code
   &device_code=...&client_id=...
   │
   └─ 轮询直到用户完成授权，返回 token
```

### Refresh Token 刷新

```
POST /oauth2/token
grant_type=refresh_token&refresh_token=...&client_id=...
│
└─ OidcAuthorizationService.findByToken()
   ├─ 内存中命中 → 正常刷新
   └─ 内存未命中（如服务重启）→ reconstructFromLegacyRefreshToken()
      从遗留 Access Token JWT 载荷中解析 appId/uid，重建 OAuth2Authorization
```

## Token 语义映射

`OidcTokenGenerator` 实现 `OAuth2TokenGenerator<OAuth2Token>`：

| OIDC token 类型 | 内部实现 | 有效期 | 说明 |
|---|---|---|---|
| `access_token` | `OidcTokenBridgeService.issueApiTicket()` | 15 分钟 | 短期 ApiTicket，可直接用于旧开放平台接口 |
| `refresh_token` | `OidcTokenBridgeService.issueLegacyAccessToken()` | 长期（无过期） | 遗留 Access Token，用于刷新 |
| `id_token` | Spring `JwtGenerator` + 自定义 `OAuth2TokenCustomizer` | - | 标准 OIDC JWT，JWK 签名 |

`OidcAuthorizationService`（混合式授权服务）：

- 内存委托（`InMemoryOAuth2AuthorizationService`）处理 auth code 阶段的短生命周期状态
- `findByToken()` 在内存未命中时，通过 `OidcTokenBridgeService` 回退到遗留 token 验证
  - `REFRESH_TOKEN` → `validateLegacyAccessToken()` 解析 JWT 载荷重建授权
  - `ACCESS_TOKEN` → `parseApiTicket()` 解析 ApiTicket 重建授权（支持 UserInfo 查询）

## 授权同意（Consent）

`OidcAuthorizationConsentService` 将 `OAuth2AuthorizationConsent` 映射到 `ThirdPartyAppAuthorization`：

- `principalName` 是用户名（来自 `UserPrincipal.getUsername()`），通过 `UserService.getUserByUser()` 解析为 uid
- `registeredClientId` 是 appId 的字符串形式
- scope 以 `SCOPE_` 前缀的 `GrantedAuthority` 形式存储，保存时去除前缀合并为空格分隔字符串
- `save()` 采用追加合并语义，不覆盖已有授权范围
- scope 为空时触发 `revoke()` 整体撤销

## UserInfo 声明映射

`OidcUserClaimsMapper.buildClaims()` 按 scope 过滤用户属性：

| scope | claims |
|---|---|
| `openid` | `sub` = uid 字符串 |
| `profile` | `preferred_username`、`name`、`picture`（头像 URL） |
| `email` | `email`、`email_verified`（固定 false） |

## 客户端元数据

`OidcRegisteredClientRepository` 将 `ThirdPartyApp` 映射为 `RegisteredClient`：

- 启用条件：`isEnabled=true` 且 `oidcEnabled=true`
- `clientId` / `id` = appId 字符串
- `clientSecret` = `{bcrypt}` + `clientSecretHash`（数据库中的 BCrypt 哈希）
- `redirectUris` = `callbackUrl`
- 默认 scopes：`openid`、`profile`、`storage_read`、`storage_write`
- 默认授权类型：`authorization_code`、`device_code`、`refresh_token`
- 认证方式映射：`CLIENT_SECRET_BASIC`（默认）、`CLIENT_SECRET_POST`、`NONE`
- PKCE：`requirePkce=true` 时 `ClientSettings.requireProofKey=true`
