package com.xiaotao.saltedfishcloud.config.security.oidc;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import com.xiaotao.saltedfishcloud.config.oidc.OidcServerProperty;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * {@link OidcAuthorizationServerConfig} 的测试类。
 * <p>
 * 使用最小化 Spring 上下文（不依赖 {@code @SpringBootTest}），仅加载授权服务器所需 Bean：
 * <ul>
 *   <li>验证 {@link AuthorizationServerSettings} Bean 包含正确的 Issuer 与端点路径</li>
 *   <li>验证 {@link OidcJwkService#generateJwkSet(OidcServerProperty)} 生成正确 keyId 的 JWKSet</li>
 *   <li>验证 OIDC 发现端点 {@code /.well-known/openid-configuration} 返回正确 JSON</li>
 * </ul>
 * </p>
 */
@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = OidcAuthorizationServerConfigTest.TestConfig.class)
@TestPropertySource(properties = "sys.oidc.enabled=true")
class OidcAuthorizationServerConfigTest {

    /**
     * 最小化测试配置，仅加载 OIDC 授权服务器所需的 Bean，不启动完整应用上下文。
     */
    @Configuration
    @EnableWebSecurity
    @Import({OidcAuthorizationServerConfig.class, OidcJwkService.class})
    static class TestConfig {

        /**
         * 提供测试用的 OIDC 配置属性，Issuer 设为 {@code https://cloud.example.com}。
         */
        @Bean
        OidcServerProperty oidcServerProperty() {
            OidcServerProperty prop = new OidcServerProperty();
            prop.setEnabled(true);
            prop.setIssuer("https://cloud.example.com");
            return prop;
        }

        /**
         * 提供测试用的注册客户端仓库（含一个最小测试客户端），满足授权服务器初始化所需的最小依赖。
         */
        @Bean
        RegisteredClientRepository registeredClientRepository() {
            RegisteredClient client = RegisteredClient.withId(UUID.randomUUID().toString())
                    .clientId("test-client")
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .redirectUri("http://localhost/callback")
                    .scope(OidcScopes.OPENID)
                    .build();
            return new InMemoryRegisteredClientRepository(client);
        }

        /**
         * 提供测试用的 {@link JwtDecoder}，从 JWK 密钥源构建，用于 OIDC UserInfo 端点的令牌验证。
         */
        @Bean
        JwtDecoder jwtDecoder(com.nimbusds.jose.jwk.source.JWKSource<SecurityContext> jwkSource) {
            return OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource);
        }
    }

    @Autowired
    private WebApplicationContext wac;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(wac)
                .apply(springSecurity())
                .build();
    }

    /**
     * 验证 {@link OidcAuthorizationServerConfig#authorizationServerSettings(OidcServerProperty)}
     * 将属性中的自定义端点路径正确映射到 {@link AuthorizationServerSettings}。
     * <p>
     * 使用显式非默认值（非 {@link OidcServerProperty} 默认值），以确保映射关系确实被验证，
     * 而非仅依赖属性默认值与框架默认值的巧合一致。
     * </p>
     */
    @Test
    void authorizationServerSettingsShouldHaveCorrectValues() {
        OidcServerProperty prop = new OidcServerProperty();
        prop.setIssuer("https://cloud.example.com");
        prop.setAuthorizationEndpoint("/custom/authorize");
        prop.setJwkSetEndpoint("/custom/jwks");

        OidcAuthorizationServerConfig config = new OidcAuthorizationServerConfig();
        AuthorizationServerSettings settings = config.authorizationServerSettings(prop);

        assertEquals("https://cloud.example.com", settings.getIssuer());
        assertEquals("/custom/authorize", settings.getAuthorizationEndpoint());
        assertEquals("/custom/jwks", settings.getJwkSetEndpoint());
    }

    /**
     * 验证 {@link OidcJwkService#generateJwkSet(OidcServerProperty)} 生成的 JWKSet
     * 包含具有正确 keyId 的 RSA 密钥。
     * <p>
     * 纯单元验证：直接实例化服务类，无需 Spring 上下文注入。
     * </p>
     */
    @Test
    void jwkServiceShouldGenerateJwkSetWithConfiguredKeyId() {
        OidcServerProperty prop = new OidcServerProperty();
        prop.getJwk().setKeyId("test-rsa-key");

        OidcJwkService service = new OidcJwkService();
        JWKSet jwkSet = service.generateJwkSet(prop);

        assertNotNull(jwkSet, "JWKSet 不应为 null");
        assertFalse(jwkSet.getKeys().isEmpty(), "JWKSet 应包含至少一个密钥");
        assertEquals("test-rsa-key", jwkSet.getKeys().get(0).getKeyID(), "密钥 ID 应与配置的 keyId 一致");
    }

    /**
     * 验证 OIDC 发现端点 {@code /.well-known/openid-configuration} 返回 HTTP 200，
     * 且响应 JSON 中的 issuer、authorization_endpoint 和 jwks_uri 符合配置值。
     */
    @Test
    void discoveryEndpointShouldReturnCorrectConfiguration() throws Exception {
        mockMvc.perform(get("/.well-known/openid-configuration"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.issuer").value("https://cloud.example.com"))
                .andExpect(jsonPath("$.authorization_endpoint").value("https://cloud.example.com/oauth2/authorize"))
                .andExpect(jsonPath("$.jwks_uri").value("https://cloud.example.com/oauth2/jwks"));
    }
}
