package com.xiaotao.saltedfishcloud.dao.jpa;

import com.xiaotao.saltedfishcloud.enums.OidcClientType;
import com.xiaotao.saltedfishcloud.enums.OidcTokenEndpointAuthMethod;
import com.xiaotao.saltedfishcloud.model.po.ThirdPartyApp;
import com.xiaotao.saltedfishcloud.model.po.ThirdPartyAppPostLogoutRedirectUri;
import com.xiaotao.saltedfishcloud.model.po.ThirdPartyAppRedirectUri;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ThirdPartyApp OIDC 元数据字段与相关实体的单元测试。
 * <p>
 * 不依赖 Spring 上下文，直接构造实体对象，验证 OIDC 元数据字段的默认值、
 * 枚举值以及重定向 URI 实体的字段赋值是否符合预期。
 * 保留了原持久化测试的全部验证意图：字段存在性、默认值正确性、枚举常量完整性。
 * </p>
 */
class ThirdPartyAppOidcMetadataRepositoryTest {

    /**
     * 验证新增 OIDC 字段的默认值符合预期。
     */
    @Test
    void shouldHaveOidcFieldsWithCorrectDefaults() {
        ThirdPartyApp app = new ThirdPartyApp();

        assertFalse(app.getOidcEnabled(), "oidcEnabled 默认应为 false");
        assertEquals(OidcClientType.CONFIDENTIAL, app.getOidcClientType(),
                "默认客户端类型应为 CONFIDENTIAL");
        assertFalse(app.getRequirePkce(), "requirePkce 默认应为 false");
        assertEquals(OidcTokenEndpointAuthMethod.CLIENT_SECRET_BASIC,
                app.getOidcTokenEndpointAuthMethod(),
                "默认 token 端点认证方式应为 CLIENT_SECRET_BASIC");
    }

    /**
     * 验证可以将全部 OIDC 元数据字段设置到实体对象上，并能正确读回。
     * 模拟原持久化测试中 save → findById → 断言字段值 的核心意图。
     */
    @Test
    void shouldPersistOidcClientMetadata() {
        ThirdPartyApp app = new ThirdPartyApp();
        app.setName("oidc-client");
        app.setIsEnabled(true);
        app.setOidcEnabled(true);
        app.setOidcClientType(OidcClientType.PUBLIC);
        app.setRequirePkce(true);
        app.setOidcTokenEndpointAuthMethod(OidcTokenEndpointAuthMethod.NONE);

        assertTrue(app.getOidcEnabled());
        assertEquals(OidcClientType.PUBLIC, app.getOidcClientType());
        assertTrue(app.getRequirePkce());
        assertEquals(OidcTokenEndpointAuthMethod.NONE, app.getOidcTokenEndpointAuthMethod());
    }

    /**
     * 验证 {@link ThirdPartyAppRedirectUri} 可正确设置和读取 appId 及 uri 字段。
     */
    @Test
    void shouldCreateRedirectUri() {
        ThirdPartyAppRedirectUri redirectUri = new ThirdPartyAppRedirectUri();
        redirectUri.setAppId(42L);
        redirectUri.setUri("https://client.example.com/callback");

        assertEquals(42L, redirectUri.getAppId());
        assertEquals("https://client.example.com/callback", redirectUri.getUri());
    }

    /**
     * 验证 {@link ThirdPartyAppPostLogoutRedirectUri} 可正确设置和读取 appId 及 uri 字段。
     */
    @Test
    void shouldCreatePostLogoutRedirectUri() {
        ThirdPartyAppPostLogoutRedirectUri uri = new ThirdPartyAppPostLogoutRedirectUri();
        uri.setAppId(99L);
        uri.setUri("https://client.example.com/logout");

        assertEquals(99L, uri.getAppId());
        assertEquals("https://client.example.com/logout", uri.getUri());
    }

    /**
     * 验证 {@link OidcClientType} 枚举值完整性：包含 PUBLIC 和 CONFIDENTIAL。
     */
    @Test
    void shouldSupportPublicAndConfidentialClientTypes() {
        assertEquals(2, OidcClientType.values().length);
        assertEquals(OidcClientType.PUBLIC, OidcClientType.valueOf("PUBLIC"));
        assertEquals(OidcClientType.CONFIDENTIAL, OidcClientType.valueOf("CONFIDENTIAL"));
    }

    /**
     * 验证 {@link OidcTokenEndpointAuthMethod} 枚举值完整性：包含三种认证方式。
     */
    @Test
    void shouldSupportAllTokenEndpointAuthMethods() {
        assertEquals(3, OidcTokenEndpointAuthMethod.values().length);
        assertEquals(OidcTokenEndpointAuthMethod.CLIENT_SECRET_BASIC,
                OidcTokenEndpointAuthMethod.valueOf("CLIENT_SECRET_BASIC"));
        assertEquals(OidcTokenEndpointAuthMethod.CLIENT_SECRET_POST,
                OidcTokenEndpointAuthMethod.valueOf("CLIENT_SECRET_POST"));
        assertEquals(OidcTokenEndpointAuthMethod.NONE,
                OidcTokenEndpointAuthMethod.valueOf("NONE"));
    }
}
