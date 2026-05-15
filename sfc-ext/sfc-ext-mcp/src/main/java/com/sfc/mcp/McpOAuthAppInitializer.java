package com.sfc.mcp;

import com.xiaotao.saltedfishcloud.dao.jpa.ThirdPartyAppRepo;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.model.po.ThirdPartyApp;
import com.xiaotao.saltedfishcloud.service.third.ThirdPartyAppService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * MCP 插件启动时自动确保系统中存在默认的第三方 OAuth 应用。
 */
@Component
@Order(4)
@Slf4j
@RequiredArgsConstructor
public class McpOAuthAppInitializer implements ApplicationRunner {

    /**
     * MCP 插件默认使用的第三方 OAuth 应用名称。
     */
    private static final String MCP_OAUTH_APP_NAME = "咸鱼云网盘MCP服务";

    /**
     * 系统自动创建数据默认使用的 UID。
     */
    private static final long SYSTEM_UID = 0L;

    /**
     * 第三方 OAuth 应用持久化仓库。
     */
    private final ThirdPartyAppRepo thirdPartyAppRepo;

    /**
     * 第三方 OAuth 应用业务服务。
     */
    private final ThirdPartyAppService thirdPartyAppService;

    /**
     * 在系统启动完成后自动检查并创建 MCP 所需的第三方 OAuth 应用。
     *
     * @param args 启动参数
     */
    @Override
    public void run(ApplicationArguments args) {
        Optional<ThirdPartyApp> existApp = findExistingMcpOauthApp();
        if (existApp.isPresent()) {
            log.info("[MCP插件] 第三方OAuth应用已存在，跳过自动创建。应用ID：{}，应用名称：{}", existApp.get().getId(), existApp.get().getName());
            return;
        }

        ThirdPartyApp app = buildDefaultMcpOauthApp();
        try {
            thirdPartyAppService.save(app);
            log.info("[MCP插件] 已自动创建第三方OAuth应用。应用ID：{}，应用名称：{}，允许永久ApiTicket：{}，回调URL：{}",
                    app.getId(),
                    app.getName(),
                    app.getAllowPermanentApiTicket(),
                    app.getCallbackUrl());
        } catch (JsonException | DataIntegrityViolationException exception) {
            Optional<ThirdPartyApp> currentApp = findExistingMcpOauthApp();
            if (currentApp.isPresent()) {
                log.info("[MCP插件] 检测到第三方OAuth应用已由其他启动流程创建，跳过重复创建。应用ID：{}，应用名称：{}",
                        currentApp.get().getId(),
                        currentApp.get().getName());
                return;
            }
            throw exception;
        }
    }

    /**
     * 按名称查询系统中是否已存在 MCP 默认 OAuth 应用。
     *
     * @return 已存在的应用对象；若不存在则返回空
     */
    private Optional<ThirdPartyApp> findExistingMcpOauthApp() {
        return thirdPartyAppRepo.findByNameIgnoreCase(MCP_OAUTH_APP_NAME);
    }

    /**
     * 构建 MCP 插件默认使用的第三方 OAuth 应用。
     *
     * @return 待保存的第三方 OAuth 应用对象
     */
    private ThirdPartyApp buildDefaultMcpOauthApp() {
        ThirdPartyApp app = new ThirdPartyApp();
        app.setUid(SYSTEM_UID);
        app.setName(MCP_OAUTH_APP_NAME);
        app.setCallbackUrl(null);
        app.setIsEnabled(true);
        app.setAllowPermanentApiTicket(true);
        return app;
    }
}

