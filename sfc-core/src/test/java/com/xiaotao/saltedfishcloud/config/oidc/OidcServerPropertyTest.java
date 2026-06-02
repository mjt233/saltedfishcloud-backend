package com.xiaotao.saltedfishcloud.config.oidc;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.BindResult;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * OidcServerProperty 配置属性绑定单元测试。
 * <p>
 * 直接使用 Binder + MapConfigurationPropertySource 验证 sys.oidc.* 属性能否正确绑定到 OidcServerProperty。
 * 不依赖 Spring 上下文。
 * </p>
 */
class OidcServerPropertyTest {
    @Test
    void shouldBindOidcPropertiesCorrectly() {
        Map<String, Object> properties = new HashMap<>();
        properties.put("sys.oidc.enabled", true);
        properties.put("sys.oidc.jwk.key-id", "custom-key-override");
        properties.put("sys.oidc.jwk.key-store-path", "./custom-jwk.json");
        // 不设置 endpoint 字段，测试默认值

        MapConfigurationPropertySource source = new MapConfigurationPropertySource(properties);
        Binder binder = new Binder(source);
        BindResult<OidcServerProperty> result = binder.bind("sys.oidc", OidcServerProperty.class);
        assertTrue(result.isBound(), "属性绑定失败");
        OidcServerProperty prop = result.get();
        assertTrue(prop.isEnabled());
        assertEquals("/oauth2/authorize", prop.getAuthorizationEndpoint());
        assertEquals("/oauth2/token", prop.getTokenEndpoint());
        assertEquals("/oauth2/userinfo", prop.getUserInfoEndpoint());
        assertEquals("custom-key-override", prop.getJwk().getKeyId());
        // 校验 JWK 其它绑定值
        assertEquals("./custom-jwk.json", prop.getJwk().getKeyStorePath());
    }

    @Test
    void shouldBindDefaultValuesWhenPropertiesMissing() {
        Map<String, Object> properties = new HashMap<>();
        // 只设置 enabled
        properties.put("sys.oidc.enabled", false);
        MapConfigurationPropertySource source = new MapConfigurationPropertySource(properties);
        Binder binder = new Binder(source);
        BindResult<OidcServerProperty> result = binder.bind("sys.oidc", OidcServerProperty.class);
        assertTrue(result.isBound(), "属性绑定失败");
        OidcServerProperty prop = result.get();
        assertFalse(prop.isEnabled());
        assertEquals("/oauth2/authorize", prop.getAuthorizationEndpoint());
        assertEquals("/oauth2/token", prop.getTokenEndpoint());
        assertEquals("/oauth2/userinfo", prop.getUserInfoEndpoint());
        assertEquals("oidc-key-1", prop.getJwk().getKeyId());
        assertEquals("./oidc-jwk.json", prop.getJwk().getKeyStorePath());
    }
}
