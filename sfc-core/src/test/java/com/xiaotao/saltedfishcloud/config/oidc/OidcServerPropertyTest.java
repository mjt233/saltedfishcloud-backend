package com.xiaotao.saltedfishcloud.config.oidc;

import com.xiaotao.saltedfishcloud.ext.PluginManager;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link OidcServerProperty} 配置绑定集成测试。
 * <p>
 * 使用 develop 环境配置（SQLite、Redis 禁用），并 Mock 掉 {@link PluginManager}，
 * 以在不依赖外部基础设施的情况下验证 {@code sys.oidc.*} 属性绑定正确性。
 * </p>
 */
@SpringBootTest(properties = {
        "sys.oidc.enabled=true",
        "sys.oidc.issuer=https://cloud.example.com",
        "sys.oidc.jwk.key-id=oidc-key-1"
})
@ActiveProfiles("develop")
class OidcServerPropertyTest {

    /** Mock 掉插件管理器，避免测试上下文因缺少 pluginManager Bean 而启动失败。 */
    @MockBean
    private PluginManager pluginManager;

    @Resource
    private OidcServerProperty property;

    /**
     * 验证 issuer、默认端点路径以及 JWK 密钥 ID 能正确绑定。
     */
    @Test
    void shouldBindIssuerAndDefaultEndpoints() {
        assertTrue(property.isEnabled());
        assertEquals("https://cloud.example.com", property.getIssuer());
        assertEquals("/oauth2/authorize", property.getAuthorizationEndpoint());
        assertEquals("/oauth2/token", property.getTokenEndpoint());
        assertEquals("/oauth2/userinfo", property.getUserInfoEndpoint());
        assertEquals("oidc-key-1", property.getJwk().getKeyId());
    }
}

