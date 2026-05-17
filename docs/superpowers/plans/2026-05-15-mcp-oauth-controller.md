# MCP OAuth Controller 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 sfc-ext-mcp 模块添加 OAuth 授权能力，允许 MCP 用户通过内部 OAuth 流程获取永久 ApiTicket，并查询已有的 ApiTicket。

**Architecture:** 在 McpOAuthAppInitializer 中增加 clientSecret 管理逻辑（分布式锁 + 缓存），新建 McpOAuthController 提供两个 REST 接口。所有改动限制在 sfc-ext-mcp 模块内。

**Tech Stack:** Spring Boot, Spring Security, JPA, CacheService (Redis), JWT

---

## 文件结构

| 文件 | 操作 | 职责 |
|------|------|------|
| `sfc-ext/sfc-ext-mcp/src/main/java/com/sfc/mcp/constant/McpConstant.java` | 新建 | MCP 模块常量定义 |
| `sfc-ext/sfc-ext-mcp/src/main/java/com/sfc/mcp/McpOAuthAppInitializer.java` | 修改 | 启动时确保 MCP 应用存在并管理 clientSecret |
| `sfc-ext/sfc-ext-mcp/src/main/java/com/sfc/mcp/controller/McpOAuthController.java` | 新建 | OAuth 接口：getApiTicket、getExistingApiTicket |

---

### Task 1: 创建 McpConstant 常量类

**Files:**
- Create: `sfc-ext/sfc-ext-mcp/src/main/java/com/sfc/mcp/constant/McpConstant.java`

- [ ] **Step 1: 创建 McpConstant 类**

```java
package com.sfc.mcp.constant;

/**
 * MCP 模块常量定义。
 */
public final class McpConstant {

    /**
     * MCP OAuth 应用名称，用于查找应用。
     */
    public static final String MCP_OAUTH_APP_NAME = "咸鱼云网盘MCP服务";

    /**
     * MCP OAuth clientSecret 缓存 key 前缀。
     */
    public static final String MCP_OAUTH_CLIENT_SECRET_CACHE_PREFIX = "sfc:mcp:oauth:client_secret:";

    /**
     * MCP OAuth 初始化分布式锁缓存 key 前缀。
     */
    public static final String MCP_OAUTH_INIT_LOCK_CACHE_PREFIX = "sfc:mcp:oauth:init_lock:";

    private McpConstant() {}
}
```

- [ ] **Step 2: 验证编译**

Run: 构建 sfc-ext-mcp 模块，确保编译通过。

- [ ] **Step 3: 提交**

```bash
git add sfc-ext/sfc-ext-mcp/src/main/java/com/sfc/mcp/constant/McpConstant.java
git commit -m "feat(mcp): 添加McpConstant常量类"
```

---

### Task 2: 修改 McpOAuthAppInitializer 管理 clientSecret

**Files:**
- Modify: `sfc-ext/sfc-ext-mcp/src/main/java/com/sfc/mcp/McpOAuthAppInitializer.java`

- [ ] **Step 1: 修改 McpOAuthAppInitializer，添加 clientSecret 管理逻辑**

将 `McpOAuthAppInitializer.java` 完整替换为以下内容：

```java
package com.sfc.mcp;

import com.sfc.mcp.constant.McpConstant;
import com.xiaotao.saltedfishcloud.cache.CacheService;
import com.xiaotao.saltedfishcloud.dao.jpa.ThirdPartyAppKeyRepo;
import com.xiaotao.saltedfishcloud.dao.jpa.ThirdPartyAppRepo;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.model.po.ThirdPartyApp;
import com.xiaotao.saltedfishcloud.model.po.ThirdPartyAppKey;
import com.xiaotao.saltedfishcloud.service.third.ThirdPartyAppKeyService;
import com.xiaotao.saltedfishcloud.service.third.ThirdPartyAppService;
import com.xiaotao.saltedfishcloud.utils.SecureUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * MCP 插件启动时自动确保系统中存在默认的第三方 OAuth 应用，并管理 clientSecret。
 */
@Component
@Order(4)
@Slf4j
@RequiredArgsConstructor
public class McpOAuthAppInitializer implements ApplicationRunner {

    /**
     * 系统自动创建数据默认使用的 UID。
     */
    private static final long SYSTEM_UID = 0L;

    /**
     * 分布式锁等待轮询间隔（毫秒）。
     */
    private static final long LOCK_POLL_INTERVAL_MS = 1000L;

    /**
     * 分布式锁等待最大次数（30秒 / 1秒 = 30次）。
     */
    private static final int LOCK_POLL_MAX_ATTEMPTS = 30;

    private final ThirdPartyAppRepo thirdPartyAppRepo;
    private final ThirdPartyAppService thirdPartyAppService;
    private final ThirdPartyAppKeyService thirdPartyAppKeyService;
    private final ThirdPartyAppKeyRepo thirdPartyAppKeyRepo;
    private final CacheService cacheService;

    @Override
    public void run(ApplicationArguments args) {
        ThirdPartyApp app = findOrCreateMcpOauthApp();
        ensureClientSecret(app);
    }

    /**
     * 查找或创建 MCP OAuth 应用。
     */
    private ThirdPartyApp findOrCreateMcpOauthApp() {
        Optional<ThirdPartyApp> existApp = thirdPartyAppRepo.findByNameIgnoreCase(McpConstant.MCP_OAUTH_APP_NAME);
        if (existApp.isPresent()) {
            log.info("[MCP插件] 第三方OAuth应用已存在，跳过自动创建。应用ID：{}，应用名称：{}",
                    existApp.get().getId(), existApp.get().getName());
            return existApp.get();
        }

        ThirdPartyApp app = buildDefaultMcpOauthApp();
        try {
            thirdPartyAppService.save(app);
            log.info("[MCP插件] 已自动创建第三方OAuth应用。应用ID：{}，应用名称：{}，允许永久ApiTicket：{}，回调URL：{}",
                    app.getId(),
                    app.getName(),
                    app.getAllowPermanentApiTicket(),
                    app.getCallbackUrl());
            return app;
        } catch (JsonException | DataIntegrityViolationException exception) {
            Optional<ThirdPartyApp> currentApp = thirdPartyAppRepo.findByNameIgnoreCase(McpConstant.MCP_OAUTH_APP_NAME);
            if (currentApp.isPresent()) {
                log.info("[MCP插件] 检测到第三方OAuth应用已由其他启动流程创建，使用已有应用。应用ID：{}，应用名称：{}",
                        currentApp.get().getId(), currentApp.get().getName());
                return currentApp.get();
            }
            throw exception;
        }
    }

    /**
     * 构建 MCP 插件默认使用的第三方 OAuth 应用。
     */
    private ThirdPartyApp buildDefaultMcpOauthApp() {
        ThirdPartyApp app = new ThirdPartyApp();
        app.setUid(SYSTEM_UID);
        app.setName(McpConstant.MCP_OAUTH_APP_NAME);
        app.setCallbackUrl(null);
        app.setIsEnabled(true);
        app.setAllowPermanentApiTicket(true);
        return app;
    }

    /**
     * 确保 MCP 应用拥有有效的 clientSecret，并缓存到 CacheService。
     * <p>
     * 流程：
     * 1. 检查缓存是否存在 clientSecret
     * 2. 不存在则通过分布式锁确保只有一个实例生成密钥
     * 3. 生成密钥后缓存 clientSecret 明文
     */
    private void ensureClientSecret(ThirdPartyApp app) {
        Long appId = app.getId();
        String cacheKey = McpConstant.MCP_OAUTH_CLIENT_SECRET_CACHE_PREFIX + appId;

        // 1. 检查缓存
        String cachedSecret = cacheService.get(cacheKey);
        if (cachedSecret != null) {
            log.info("[MCP插件] clientSecret 已从缓存加载。应用ID：{}", appId);
            return;
        }

        // 2. 尝试获取分布式锁
        String lockKey = McpConstant.MCP_OAUTH_INIT_LOCK_CACHE_PREFIX + appId;
        boolean locked = cacheService.setIfAbsent(lockKey, "1", 30, TimeUnit.SECONDS);

        if (locked) {
            try {
                // 双重检查
                cachedSecret = cacheService.get(cacheKey);
                if (cachedSecret != null) {
                    log.info("[MCP插件] clientSecret 已由其他流程写入缓存。应用ID：{}", appId);
                    return;
                }

                // 清理旧密钥并生成新密钥
                thirdPartyAppKeyService.deleteByAppId(java.util.Collections.singletonList(appId));
                String rawKey = createAndSaveKey(app);
                cacheService.set(cacheKey, rawKey);
                log.info("[MCP插件] 已生成并缓存 clientSecret。应用ID：{}", appId);
            } finally {
                cacheService.delete(lockKey);
            }
        } else {
            // 3. 其他实例在处理，轮询等待
            log.info("[MCP插件] 等待其他实例完成 clientSecret 初始化...应用ID：{}", appId);
            for (int i = 0; i < LOCK_POLL_MAX_ATTEMPTS; i++) {
                try {
                    Thread.sleep(LOCK_POLL_INTERVAL_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new JsonException("等待 clientSecret 初始化被中断");
                }
                cachedSecret = cacheService.get(cacheKey);
                if (cachedSecret != null) {
                    log.info("[MCP插件] clientSecret 已从其他实例的初始化中获取。应用ID：{}", appId);
                    return;
                }
            }
            throw new JsonException("等待 clientSecret 初始化超时");
        }
    }

    /**
     * 直接创建 ThirdPartyAppKey 实体并保存。
     * <p>
     * 不调用 ThirdPartyAppKeyService.generateNewKey()，因为该方法内部调用
     * SecureUtils.getCurrentUid()，启动时无安全上下文会返回 null。
     *
     * @param app MCP OAuth 应用
     * @return clientSecret 明文
     */
    private String createAndSaveKey(ThirdPartyApp app) {
        String rawKey = SecureUtils.getUUID();
        ThirdPartyAppKey key = new ThirdPartyAppKey();
        key.setName("MCP Internal");
        key.setAppId(app.getId());
        key.setUid(SYSTEM_UID);
        key.setClientSecretHash(SecureUtils.getBCryptPasswordEncoder().encode(rawKey));
        key.setClientSecretMaskValue(rawKey.substring(0, 6) + "******" + rawKey.substring(rawKey.length() - 6));
        thirdPartyAppKeyRepo.save(key);
        return rawKey;
    }
}
```

- [ ] **Step 2: 验证编译**

Run: 构建 sfc-ext-mcp 模块，确保编译通过。

- [ ] **Step 3: 提交**

```bash
git add sfc-ext/sfc-ext-mcp/src/main/java/com/sfc/mcp/McpOAuthAppInitializer.java
git commit -m "feat(mcp): McpOAuthAppInitializer增加clientSecret管理逻辑"
```

---

### Task 3: 创建 McpOAuthController

**Files:**
- Create: `sfc-ext/sfc-ext-mcp/src/main/java/com/sfc/mcp/controller/McpOAuthController.java`

- [ ] **Step 1: 创建 McpOAuthController 类**

```java
package com.sfc.mcp.controller;

import com.sfc.mcp.constant.McpConstant;
import com.xiaotao.saltedfishcloud.cache.CacheService;
import com.xiaotao.saltedfishcloud.dao.jpa.ThirdPartyAppRepo;
import com.xiaotao.saltedfishcloud.dao.jpa.ThirdPartyAppTokenRepo;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.model.json.JsonResult;
import com.xiaotao.saltedfishcloud.model.json.JsonResultImpl;
import com.xiaotao.saltedfishcloud.model.po.ThirdPartyApp;
import com.xiaotao.saltedfishcloud.model.po.ThirdPartyAppToken;
import com.xiaotao.saltedfishcloud.service.third.ThirdPartyAppTokenService;
import com.xiaotao.saltedfishcloud.utils.SecureUtils;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * MCP OAuth 授权控制器。
 * <p>
 * 提供 MCP 专用的 OAuth 授权接口，支持通过授权码换取永久 ApiTicket，
 * 以及查询当前用户已有的 ApiTicket。
 */
@RestController
@RequestMapping("/api/mcp/oauth")
@Api(tags = "MCP OAuth 授权")
@RequiredArgsConstructor
public class McpOAuthController {

    private final ThirdPartyAppTokenService thirdPartyAppTokenService;
    private final ThirdPartyAppRepo thirdPartyAppRepo;
    private final ThirdPartyAppTokenRepo thirdPartyAppTokenRepo;
    private final CacheService cacheService;

    /**
     * 通过授权码换取永久 ApiTicket。
     * <p>
     * 如果该用户已存在 MCP 应用的永久 ApiTicket，旧的会被自动作废。
     *
     * @param code OAuth authorize 重定向返回的授权码
     * @return 完整的 ApiTicket JWT
     */
    @ApiOperation("通过授权码换取永久ApiTicket")
    @GetMapping("/getApiTicket")
    public JsonResult<String> getApiTicket(@RequestParam("code") String code) {
        ThirdPartyApp app = getMcpApp();
        String clientSecret = getClientSecret(app.getId());
        Long uid = SecureUtils.getCurrentUid();

        String accessToken = thirdPartyAppTokenService.getAccessToken(code, clientSecret);
        String apiTicket = thirdPartyAppTokenService.getApiTicket(app.getId(), uid, accessToken, true);
        return JsonResultImpl.getInstance(apiTicket);
    }

    /**
     * 查询当前用户已有的 MCP 应用永久 ApiTicket。
     * <p>
     * 返回遮掩后的 ApiTicket（前6位 + ****** + 后6位）。
     * 如果没有有效的 ApiTicket，返回 null。
     *
     * @return 遮掩后的 ApiTicket 或 null
     */
    @ApiOperation("查询已有的永久ApiTicket")
    @GetMapping("/getExistingApiTicket")
    public JsonResult<String> getExistingApiTicket() {
        ThirdPartyApp app = getMcpApp();
        Long uid = SecureUtils.getCurrentUid();

        ThirdPartyAppToken tokenRecord = thirdPartyAppTokenRepo.findByAppIdAndUid(app.getId(), uid);
        if (tokenRecord == null || tokenRecord.getApiTicket() == null) {
            return JsonResultImpl.getInstance(null);
        }

        try {
            thirdPartyAppTokenService.parseAndValidateApiTicket(tokenRecord.getApiTicket());
            return JsonResultImpl.getInstance(maskApiTicket(tokenRecord.getApiTicket()));
        } catch (JsonException e) {
            // ApiTicket 无效或已撤销
            return JsonResultImpl.getInstance(null);
        }
    }

    /**
     * 获取 MCP OAuth 应用。
     */
    private ThirdPartyApp getMcpApp() {
        return thirdPartyAppRepo.findByNameIgnoreCase(McpConstant.MCP_OAUTH_APP_NAME)
                .orElseThrow(() -> new JsonException("MCP OAuth应用不存在，请检查系统初始化是否正常"));
    }

    /**
     * 从缓存获取 MCP 应用的 clientSecret。
     */
    private String getClientSecret(Long appId) {
        String cacheKey = McpConstant.MCP_OAUTH_CLIENT_SECRET_CACHE_PREFIX + appId;
        String secret = cacheService.get(cacheKey);
        if (secret == null) {
            throw new JsonException("MCP应用clientSecret未初始化，请检查系统启动日志");
        }
        return secret;
    }

    /**
     * 遮掩 ApiTicket，只保留首尾各6位字符，中间使用6个*代替。
     */
    private String maskApiTicket(String apiTicket) {
        if (apiTicket == null || apiTicket.length() <= 12) {
            return "******";
        }
        return apiTicket.substring(0, 6) + "******" + apiTicket.substring(apiTicket.length() - 6);
    }
}
```

- [ ] **Step 2: 验证编译**

Run: 构建 sfc-ext-mcp 模块，确保编译通过。

- [ ] **Step 3: 提交**

```bash
git add sfc-ext/sfc-ext-mcp/src/main/java/com/sfc/mcp/controller/McpOAuthController.java
git commit -m "feat(mcp): 添加McpOAuthController实现getApiTicket和getExistingApiTicket接口"
```

---

### Task 4: 完整构建验证

- [ ] **Step 1: 构建整个项目**

Run: 执行完整项目构建，确保所有模块编译通过。

- [ ] **Step 2: 提交所有变更**

```bash
git status
```

确认所有变更已提交，工作区干净。

---

## 自查清单

- [x] 设计文档中的所有需求都有对应任务
- [x] 没有 TBD、TODO 或占位符
- [x] 所有类型、方法签名、属性名称一致
- [x] 所有代码步骤包含完整实现
- [x] 所有文件路径精确
