package com.xiaotao.saltedfishcloud.config.security.oidc;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import com.xiaotao.saltedfishcloud.config.oidc.OidcServerProperty;
import com.xiaotao.saltedfishcloud.config.security.SecurityConfig;
import com.xiaotao.saltedfishcloud.helper.Md5PasswordEncoder;
import com.xiaotao.saltedfishcloud.service.log.LogRecordManager;
import com.xiaotao.saltedfishcloud.service.oidc.OidcUserClaimsMapper;
import com.xiaotao.saltedfishcloud.service.third.ThirdPartyAppApiTicketService;
import com.xiaotao.saltedfishcloud.service.third.ThirdPartyAppAuthorizationService;
import com.xiaotao.saltedfishcloud.service.third.ThirdPartyAppTokenService;
import com.xiaotao.saltedfishcloud.service.user.UserService;
import com.xiaotao.saltedfishcloud.dao.redis.TokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.oauth2.server.authorization.web.OAuth2AuthorizationEndpointFilter;
import org.springframework.security.oauth2.server.authorization.web.OAuth2DeviceAuthorizationEndpointFilter;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.handler.HandlerMappingIntrospector;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

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
    @Import({SecurityConfig.class, OidcAuthorizationServerConfig.class, OidcJwkService.class})
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
                    .clientSecret(new Md5PasswordEncoder().encode("test-secret"))
                    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .authorizationGrantType(AuthorizationGrantType.DEVICE_CODE)
                    .redirectUri("http://localhost/callback")
                    .scope("profile")
                    .build();
            return new InMemoryRegisteredClientRepository(client);
        }

        /** 提供 {@link ThirdPartyAppApiTicketService} 测试桩，满足 {@link com.xiaotao.saltedfishcloud.service.oidc.OidcTokenBridgeService} 的依赖。 */
        @Bean
        ThirdPartyAppApiTicketService thirdPartyAppApiTicketService() {
            return Mockito.mock(ThirdPartyAppApiTicketService.class);
        }

        /** 提供 {@link ThirdPartyAppTokenService} 测试桩，满足 {@link com.xiaotao.saltedfishcloud.service.oidc.OidcTokenBridgeService} 的依赖。 */
        @Bean
        ThirdPartyAppTokenService thirdPartyAppTokenService() {
            return Mockito.mock(ThirdPartyAppTokenService.class);
        }

        /** 提供 {@link ThirdPartyAppAuthorizationService} 测试桩，满足 {@link com.xiaotao.saltedfishcloud.service.oidc.OidcAuthorizationConsentService} 的依赖。 */
        @Bean
        ThirdPartyAppAuthorizationService thirdPartyAppAuthorizationService() {
            return Mockito.mock(ThirdPartyAppAuthorizationService.class);
        }

        /** 提供 {@link LogRecordManager} 测试桩，满足主安全链依赖。 */
        @Bean
        LogRecordManager logRecordManager() {
            return Mockito.mock(LogRecordManager.class);
        }

        /** 提供 {@link TokenService} 测试桩，满足主安全链依赖。 */
        @Bean
        TokenService tokenService() {
            return Mockito.mock(TokenService.class);
        }

        /** 提供 {@link UserService} 测试桩，满足 {@link OidcUserClaimsMapper} 的依赖。 */
        @Bean
        UserService userService() {
            return Mockito.mock(UserService.class);
        }

        /** 提供 {@link org.springframework.security.core.userdetails.UserDetailsService} 测试桩，满足主安全链依赖。 */
        @Bean
        org.springframework.security.core.userdetails.UserDetailsService userDetailsService() {
            return Mockito.mock(org.springframework.security.core.userdetails.UserDetailsService.class);
        }

        /** 提供空的 {@link RequestMappingHandlerMapping} 测试桩，满足主安全链依赖。 */
        @Bean
        RequestMappingHandlerMapping requestMappingHandlerMapping() {
            RequestMappingHandlerMapping mapping = Mockito.mock(RequestMappingHandlerMapping.class);
            Mockito.when(mapping.getHandlerMethods()).thenReturn(Collections.<RequestMappingInfo, HandlerMethod>emptyMap());
            return mapping;
        }

        /** 提供 {@link HandlerMappingIntrospector} 测试桩，满足主安全链中的 MvcRequestMatcher 依赖。 */
        @Bean(name = "mvcHandlerMappingIntrospector")
        HandlerMappingIntrospector handlerMappingIntrospector() {
            return new HandlerMappingIntrospector();
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

    @Autowired
    @Qualifier("oidcSecurityFilterChain")
    private SecurityFilterChain oidcSecurityFilterChain;

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
        prop.setDeviceAuthorizationEndpoint("/custom/device_authorization");
        prop.setDeviceVerificationEndpoint("/custom/device_verification");
        prop.setJwkSetEndpoint("/custom/jwks");

        OidcAuthorizationServerConfig config = new OidcAuthorizationServerConfig();
        AuthorizationServerSettings settings = config.authorizationServerSettings(prop);

        assertEquals("https://cloud.example.com", settings.getIssuer());
        assertEquals("/custom/authorize", settings.getAuthorizationEndpoint());
        assertEquals("/custom/device_authorization", settings.getDeviceAuthorizationEndpoint());
        assertEquals("/custom/device_verification", settings.getDeviceVerificationEndpoint());
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
                .andExpect(jsonPath("$.device_authorization_endpoint").value("https://cloud.example.com/oauth2/device_authorization"))
                .andExpect(jsonPath("$.jwks_uri").value("https://cloud.example.com/oauth2/jwks"));
    }

    /**
     * 验证未登录用户访问授权端点时，会被重定向到系统授权登录页，而不是落回主安全链的 JSON 403。
     */
    @Test
    void authorizeEndpointShouldRedirectUnauthenticatedBrowserRequestsToOauthPage() throws Exception {
        mockMvc.perform(get("/oauth2/authorize")
                        .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                        .queryParam("response_type", "code")
                        .queryParam("client_id", "test-client")
                        .queryParam("scope", "profile")
                        .queryParam("redirect_uri", "http://localhost/callback"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/oauth?*client_id=test-client*"));
    }

    /**
     * 验证 OIDC 授权服务器过滤器链本身已包含授权端点过滤器。
     */
    @Test
    void oidcSecurityFilterChainShouldContainAuthorizationEndpointFilter() {
        String filterSummary = oidcSecurityFilterChain.getFilters().stream()
                .map(filter -> filter.getClass().getName())
                .collect(Collectors.joining(", "));
        assertTrue(oidcSecurityFilterChain.getFilters().stream()
                .anyMatch(OAuth2AuthorizationEndpointFilter.class::isInstance), "OIDC 过滤器链缺少 OAuth2AuthorizationEndpointFilter，实际过滤器为: " + filterSummary);
    }

    /**
     * 验证设备授权过滤器返回的 {@code verification_uri} 已指向系统自带激活页。
     */
    @Test
    void deviceAuthorizationFilterShouldUseCustomVerificationUri() {
        OAuth2DeviceAuthorizationEndpointFilter filter = oidcSecurityFilterChain.getFilters().stream()
                .filter(OAuth2DeviceAuthorizationEndpointFilter.class::isInstance)
                .map(OAuth2DeviceAuthorizationEndpointFilter.class::cast)
                .findFirst()
                .orElseThrow(() -> new AssertionError("OIDC 过滤器链缺少 OAuth2DeviceAuthorizationEndpointFilter"));

        assertEquals("/oauth/device", ReflectionTestUtils.getField(filter, "verificationUri"));
    }

    /**
     * 验证设备授权端点的响应会向客户端返回系统自带激活页地址。
     */
    @Test
    void deviceAuthorizationEndpointShouldReturnActivationPageAsVerificationUri() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/oauth2/device_authorization")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic("test-client", "test-secret"))
                        .param("scope", "profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verification_uri").value("http://localhost/oauth/device"));
    }

    /**
     * 验证 Spring 应用上下文中存在 {@link OidcUserClaimsMapper} Bean，
     * 说明 userinfo 声明映射器已通过 {@link OidcAuthorizationServerConfig} 正确注册。
     */
    @Autowired
    private OidcUserClaimsMapper oidcUserClaimsMapper;

    @Test
    void oidcUserClaimsMapperBeanShouldBeRegisteredInContext() {
        assertNotNull(oidcUserClaimsMapper, "OidcUserClaimsMapper Bean 应已在 Spring 上下文中注册");
    }
}
