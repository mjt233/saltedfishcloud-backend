# MCP OAuth Controller 设计文档

## 概述

为 `sfc-ext-mcp` 模块新增 OAuth 授权能力，允许 MCP 用户通过内部 OAuth 流程获取永久 ApiTicket，并查询已有的 ApiTicket。

## 约束

- 仅修改 `sfc-ext-mcp` 模块内的代码，不改动其他模块
- 复用现有 `ThirdPartyAppTokenService`、`ThirdPartyAppKeyService` 等 service
- 不新增数据库实体，不改表结构
- 接口不允许匿名访问

## 架构设计

### 涉及文件

| 文件 | 操作 | 说明 |
|------|------|------|
| `sfc-ext/sfc-ext-mcp/.../McpOAuthAppInitializer.java` | 修改 | 启动时确保 MCP 应用拥有 clientSecret 并缓存，新增注入 `ThirdPartyAppKeyService`、`ThirdPartyAppKeyRepo`、`CacheService` |
| `sfc-ext/sfc-ext-mcp/.../controller/McpOAuthController.java` | 新建 | 两个 OAuth 接口 |
| `sfc-ext/sfc-ext-mcp/.../constant/McpConstant.java` | 新建 | MCP 模块常量（缓存 key 前缀、应用名等） |

### 常量定义（McpConstant）

```java
public final class McpConstant {
    /** MCP OAuth 应用名称，用于查找应用 */
    public static final String MCP_OAUTH_APP_NAME = "咸鱼云网盘MCP服务";

    /** MCP OAuth clientSecret 缓存 key 前缀 */
    public static final String MCP_OAUTH_CLIENT_SECRET_CACHE_PREFIX = "sfc:mcp:oauth:client_secret:";

    /** MCP OAuth 初始化分布式锁缓存 key 前缀 */
    public static final String MCP_OAUTH_INIT_LOCK_CACHE_PREFIX = "sfc:mcp:oauth:init_lock:";

    private McpConstant() {}
}
```

将原来 `McpOAuthAppInitializer` 中的 `MCP_OAUTH_APP_NAME` 常量迁移至此处统一管理。

### 数据流

```
用户 → McpOAuthController
         ├─ GET /getApiTicket?code=xxx
         │    → CacheService 获取 clientSecret
         │    → ThirdPartyAppTokenService.getAccessToken(code, clientSecret)
         │    → ThirdPartyAppTokenService.getApiTicket(appId, uid, accessToken, permanent=true)
         │    → 返回完整 ApiTicket
         │
         └─ GET /getExistingApiTicket
              → ThirdPartyAppTokenRepo.findByAppIdAndUid(appId, uid)
              → ThirdPartyAppTokenService.parseAndValidateApiTicket(apiTicket)
              → 返回遮掩后的 ApiTicket
```

## ClientSecret 管理方案

### 问题

`McpOAuthAppInitializer` 启动时创建 MCP OAuth 应用但不创建密钥。`getAccessToken(code, clientSecret)` 流程需要有效的 clientSecret，而 clientSecret 明文仅在生成密钥时可获取一次，之后数据库只存储 BCrypt 哈希。

### 方案

在 `McpOAuthAppInitializer.run()` 中，确保应用存在后执行 `ensureClientSecret(app)` 流程：

```
ensureClientSecret(app):
  1. cacheKey = McpConstant.MCP_OAUTH_CLIENT_SECRET_CACHE_PREFIX + appId
  2. 从 CacheService 读取 clientSecret
     → 命中：直接返回
  3. lockKey = McpConstant.MCP_OAUTH_INIT_LOCK_CACHE_PREFIX + appId
  4. cacheService.setIfAbsent(lockKey, "1", 30s) 尝试获取分布式锁
     → 成功（获得锁）：
        a. 再次检查缓存（双重检查，防止并发）
        b. ThirdPartyAppKeyService.deleteByAppId(appId) 清理旧密钥
        c. 直接创建密钥实体（见下方说明）
        d. 将 clientSecret 明文写入 CacheService（无过期时间）
        e. 释放锁（删除 lockKey）
     → 失败（其他实例在处理）：
        a. 轮询等待缓存写入（最多 30s，间隔 1s）
        b. 读取到值后返回
        c. 超时抛出异常
```

**注意**：不直接调用 `ThirdPartyAppKeyService.generateNewKey()`，因为该方法内部调用 `SecureUtils.getCurrentUid()`，启动时无安全上下文会返回 null。改为直接创建 `ThirdPartyAppKey` 实体：

```java
String rawKey = SecureUtils.getUUID();
ThirdPartyAppKey key = new ThirdPartyAppKey();
key.setName("MCP Internal");
key.setAppId(app.getId());
key.setUid(SYSTEM_UID);  // 使用系统 UID（0L）
key.setClientSecretHash(SecureUtils.getBCryptPasswordEncoder().encode(rawKey));
key.setClientSecretMaskValue(rawKey.substring(0, 6) + "******" + rawKey.substring(rawKey.length() - 6));
thirdPartyAppKeyRepo.save(key);
// rawKey 即为 clientSecret 明文，写入缓存
```

### 安全性保证

- **集群安全**：`setIfAbsent` 作为分布式锁，确保同一时刻只有一个实例执行密钥生成
- **密钥不膨胀**：每次生成前清理旧密钥，数据库中 MCP 应用始终最多 1 个密钥
- **清理旧密钥安全**：clientSecret 仅在 `getAccessToken()` 交换令牌时使用，已获取的 Access Token 存储在 `ThirdPartyAppToken` 表中，不依赖 clientSecret
- **缓存丢失恢复**：Redis 重启后缓存丢失，下次启动自动重新生成，不影响已有 Access Token

## 接口设计

### Controller

- **类**：`com.sfc.mcp.controller.McpOAuthController`
- **路径**：`/api/mcp/oauth`
- **注解**：`@RestController`

### 接口一：`GET /getApiTicket`

通过授权码换取永久 ApiTicket。

| 项目 | 说明 |
|------|------|
| 方法 | GET |
| 路径 | `/api/mcp/oauth/getApiTicket` |
| 鉴权 | 需要用户登录 |
| 参数 | `code` (String) — OAuth authorize 重定向返回的授权码 |
| 响应 | `JsonResult<String>` — 完整的 ApiTicket JWT |

**流程**：

1. 通过 `ThirdPartyAppRepo.findByNameIgnoreCase(MCP_OAUTH_APP_NAME)` 获取 MCP 应用
2. 从 `CacheService` 获取缓存的 clientSecret
3. 调用 `thirdPartyAppTokenService.getAccessToken(code, clientSecret)` 获取 Access Token
4. 通过 `SecureUtils.getCurrentUid()` 获取当前用户 ID
5. 调用 `thirdPartyAppTokenService.getApiTicket(appId, uid, accessToken, true)` 获取永久 ApiTicket
6. 返回完整 ApiTicket

**说明**：`getApiTicket` 内部已通过 `revokeSameTypeApiTickets()` 自动作废旧的同类型票据，满足"作废原有 ApiTicket"的需求。

### 接口二：`GET /getExistingApiTicket`

查询当前用户已有的 MCP 应用永久 ApiTicket。

| 项目 | 说明 |
|------|------|
| 方法 | GET |
| 路径 | `/api/mcp/oauth/getExistingApiTicket` |
| 鉴权 | 需要用户登录 |
| 参数 | 无 |
| 响应 | `JsonResult<String>` — 遮掩后的 ApiTicket 或 null |

**流程**：

1. 获取当前用户 ID（`SecureUtils.getCurrentUid()`）
2. 获取 MCP 应用 ID
3. 查询 `ThirdPartyAppTokenRepo.findByAppIdAndUid(appId, uid)` 获取令牌记录
4. 如果令牌记录存在且有 apiTicket：
   - 调用 `thirdPartyAppTokenService.parseAndValidateApiTicket(apiTicket)` 验证有效性
   - 有效：返回遮掩版本（前6位 + `******` + 后6位）
   - 无效/已撤销：返回 null
5. 如果没有令牌记录：返回 null

## 错误处理

| 场景 | 处理方式 |
|------|----------|
| 授权码无效/过期 | `ThirdPartyAppTokenServiceImpl` 抛出 `JsonException(OAuthError.INVALID_CODE)` |
| clientSecret 无效 | 抛出 `JsonException(OAuthError.CLIENT_SECRET_INVALID)` |
| MCP 应用不存在 | 抛出 `JsonException`，附带描述信息 |
| clientSecret 未缓存且初始化失败 | 抛出 `JsonException`，提示系统初始化异常 |
| 查询时无 ApiTicket | 返回 `JsonResult` 包装的 null |
