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

import java.util.Collections;
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
        app.setCallbackUrl("");
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
                thirdPartyAppKeyService.deleteByAppId(Collections.singletonList(appId));
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
        key.setClientSecretMaskValue(rawKey.substring(0, 6) + "*".repeat(rawKey.length() - 12) + rawKey.substring(rawKey.length() - 6));
        thirdPartyAppKeyRepo.save(key);
        return rawKey;
    }
}
