package com.xiaotao.saltedfishcloud.config.security.oidc;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import com.xiaotao.saltedfishcloud.config.oidc.OidcServerProperty;
import com.xiaotao.saltedfishcloud.config.security.JwtAuthenticationFilter;
import com.xiaotao.saltedfishcloud.controller.OidcDeviceController;
import com.xiaotao.saltedfishcloud.dao.jpa.ThirdPartyAppKeyRepo;
import com.xiaotao.saltedfishcloud.service.oidc.OidcAuthorizationConsentService;
import com.xiaotao.saltedfishcloud.service.oidc.OidcAuthorizationService;
import com.xiaotao.saltedfishcloud.service.oidc.OidcRegisteredClientRepository;
import com.xiaotao.saltedfishcloud.service.oidc.OidcTokenBridgeService;
import com.xiaotao.saltedfishcloud.service.oidc.OidcTokenGenerator;
import com.xiaotao.saltedfishcloud.service.oidc.OidcUserClaimsMapper;
import com.xiaotao.saltedfishcloud.service.third.ThirdPartyAppApiTicketService;
import com.xiaotao.saltedfishcloud.service.third.ThirdPartyAppAuthorizationService;
import com.xiaotao.saltedfishcloud.service.third.ThirdPartyAppService;
import com.xiaotao.saltedfishcloud.service.third.ThirdPartyAppTokenService;
import com.xiaotao.saltedfishcloud.service.user.UserService;
import com.xiaotao.saltedfishcloud.utils.SecureUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.authorization.InMemoryOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.authentication.ClientSecretAuthenticationProvider;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.JwtGenerator;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.preauth.AbstractPreAuthenticatedProcessingFilter;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;

/**
 * OIDC 授权服务器安全配置类。
 * <p>
 * 注册 OAuth2/OIDC 授权服务器所需的核心 Bean：
 * <ul>
 *   <li>{@link SecurityFilterChain}（最高优先级，仅拦截 OAuth2/OIDC 标准端点）</li>
 *   <li>{@link AuthorizationServerSettings}（Issuer 地址与各端点路径）</li>
 *   <li>{@link com.nimbusds.jose.jwk.source.JWKSource}（JWT 签名与 JWKS 端点密钥源）</li>
 *   <li>{@link RegisteredClientRepository} → {@link OidcRegisteredClientRepository}（ThirdPartyApp 适配器）</li>
 *   <li>{@link OAuth2AuthorizationConsentService} → {@link OidcAuthorizationConsentService}（授权同意适配器）</li>
 *   <li>{@link OAuth2AuthorizationService} → {@link OidcAuthorizationService}（混合内存+遗留 token 授权服务）</li>
 *   <li>{@link OidcTokenBridgeService}（OIDC token 与遗留 token 的桥接服务）</li>
 *   <li>{@link OidcTokenGenerator}（自定义 token 生成器：ApiTicket + 遗留 Access Token + id_token）</li>
 *   <li>{@link OidcUserClaimsMapper}（OIDC UserInfo 声明映射器，按 scope 过滤用户属性）</li>
 * </ul>
 * 该过滤器链与应用主过滤器链（{@code SecurityConfig.filterChain}）共存：
 * 本链通过 {@code securityMatcher} 仅匹配授权服务器端点，主链作为低优先级后备处理其余请求。
 * </p>
 */
@Configuration
@ConditionalOnProperty(name = "sys.oidc.enabled", havingValue = "true")
public class OidcAuthorizationServerConfig {

    @Bean
    public ClientSecretAuthenticationProvider authenticationProvider(
            RegisteredClientRepository registeredClientRepository,
            OAuth2AuthorizationService oAuth2AuthorizationService) {
        ClientSecretAuthenticationProvider authenticationProvider = new ClientSecretAuthenticationProvider(registeredClientRepository, oAuth2AuthorizationService);
        authenticationProvider.setPasswordEncoder(SecureUtils.getBCryptPasswordEncoder());
        return authenticationProvider;
    }

    /**
     * 配置 OIDC 授权服务器安全过滤器链。
     * <p>
     * 仅匹配授权服务器标准端点（{@code /oauth2/**}、{@code /.well-known/**} 等），
     * 对来自浏览器的未认证请求重定向至 {@code /oauth} 登录页。
     * 优先级设为 {@link Ordered#HIGHEST_PRECEDENCE}，确保在应用主链之前处理 OIDC 请求。
     * </p>
     * <p>
     * UserInfo 端点使用 {@link OidcUserClaimsMapper#toOidcUserInfo} 自定义声明映射，
     * 按授权 scope 返回对应的用户属性。
     * </p>
     *
     * @param http         Spring Security {@link HttpSecurity} 构建器
     * @param claimsMapper OIDC UserInfo 声明映射器
     * @return 授权服务器安全过滤器链
     * @throws Exception 如果构建过滤器链失败
     */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public SecurityFilterChain oidcSecurityFilterChain(HttpSecurity http,
                                                       OidcUserClaimsMapper claimsMapper,
                                                       AuthorizationServerSettings settings,
                                                       JwtAuthenticationFilter jwtAuthenticationFilter,
                                                       AuthenticationProvider authenticationProvider) throws Exception {
        OAuth2AuthorizationServerConfigurer authorizationServer =
                OAuth2AuthorizationServerConfigurer.authorizationServer();
        http.with(authorizationServer, server -> server
                        .authorizationServerSettings(settings)
                        .oidc(oidc -> oidc.userInfoEndpoint(e -> e.userInfoMapper(claimsMapper::toOidcUserInfo)))
                        .deviceAuthorizationEndpoint(deviceAuthorization ->
                                deviceAuthorization.verificationUri(OidcDeviceController.DEVICE_ACTIVATION_PATH))
                        .deviceVerificationEndpoint(deviceVerification ->
                                deviceVerification.consentPage(OidcDeviceController.DEVICE_CONSENT_PATH)))
                .addFilterBefore(jwtAuthenticationFilter, AbstractPreAuthenticatedProcessingFilter.class)
                .authenticationProvider(authenticationProvider)
                .securityMatcher(authorizationServer.getEndpointsMatcher())
                .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
                .csrf(csrf -> csrf.ignoringRequestMatchers(authorizationServer.getEndpointsMatcher()))
                .exceptionHandling(ex -> ex.defaultAuthenticationEntryPointFor(
                        new OidcLoginRedirectEntryPoint("/oauth"),
                        new MediaTypeRequestMatcher(MediaType.TEXT_HTML, MediaType.APPLICATION_XHTML_XML)
                ));
        return http.build();
    }

    /**
     * 配置授权服务器全局设置，包含 Issuer 地址和各 OAuth2/OIDC 端点路径。
     * <p>
     * 所有端点路径从 {@link OidcServerProperty} 读取，可通过 {@code sys.oidc.*} 属性覆盖默认值。
     * </p>
     *
     * @param property OIDC 服务端配置属性
     * @return 授权服务器设置
     */
    @Bean
    public AuthorizationServerSettings authorizationServerSettings(OidcServerProperty property) {
        return AuthorizationServerSettings.builder()
                .issuer(property.getIssuer())
                .authorizationEndpoint(property.getAuthorizationEndpoint())
                .deviceAuthorizationEndpoint(property.getDeviceAuthorizationEndpoint())
                .deviceVerificationEndpoint(property.getDeviceVerificationEndpoint())
                .tokenEndpoint(property.getTokenEndpoint())
                .tokenRevocationEndpoint(property.getRevocationEndpoint())
                .tokenIntrospectionEndpoint(property.getIntrospectionEndpoint())
                .oidcUserInfoEndpoint(property.getUserInfoEndpoint())
                .oidcLogoutEndpoint(property.getLogoutEndpoint())
                .jwkSetEndpoint(property.getJwkSetEndpoint())
                .build();
    }

    /**
     * 创建用于 JWT 令牌签名和 JWKS 端点的 RSA 密钥源。
     * <p>
     * 密钥由 {@link OidcJwkService#generateJwkSet(OidcServerProperty)} 生成，
     * keyId 来自 {@link OidcServerProperty.Jwk#getKeyId()}。
     * </p>
     *
     * @param jwkService JWK 密钥服务
     * @param property   OIDC 服务端配置属性
     * @return JWK 密钥源
     */
    @Bean
    public com.nimbusds.jose.jwk.source.JWKSource<SecurityContext> jwkSource(
            OidcJwkService jwkService, OidcServerProperty property) {
        JWKSet jwkSet = jwkService.generateJwkSet(property);
        return new ImmutableJWKSet<>(jwkSet);
    }

    /**
     * 注册 {@link RegisteredClientRepository} Bean，将系统内部 {@link com.xiaotao.saltedfishcloud.model.po.ThirdPartyApp}
     * 适配为 Spring Authorization Server 的注册客户端仓库。
     *
     * @param appService       第三方应用服务
     * @param keyRepo          第三方应用密钥仓库
     * @return OIDC 注册客户端仓库
     */
    @Bean
    public RegisteredClientRepository registeredClientRepository(
            ThirdPartyAppService appService,
            ThirdPartyAppKeyRepo keyRepo) {
        return new OidcRegisteredClientRepository(appService, keyRepo);
    }

    /**
     * 注册 {@link OAuth2AuthorizationConsentService} Bean，将系统内部授权服务适配为
     * Spring Authorization Server 的授权同意服务。
     *
     * @param authorizationService 第三方应用用户授权服务
     * @return OIDC 授权同意服务
     */
    @Bean
    public OAuth2AuthorizationConsentService authorizationConsentService(
            ThirdPartyAppAuthorizationService authorizationService,
            UserService userService) {
        return new OidcAuthorizationConsentService(authorizationService, userService);
    }

    /**
     * 注册 {@link OidcTokenBridgeService} Bean。
     * <p>
     * 封装 ApiTicket 签发与遗留 Access Token 签发逻辑，为自定义 token 生成器提供统一入口。
     * </p>
     *
     * @param apiTicketService API 票据服务
     * @param tokenService     第三方应用 token 服务
     * @return OIDC token 桥接服务
     */
    @Bean
    public OidcTokenBridgeService oidcTokenBridgeService(
            ThirdPartyAppApiTicketService apiTicketService,
            ThirdPartyAppTokenService tokenService,
            ThirdPartyAppAuthorizationService authorizationService) {
        return new OidcTokenBridgeService(apiTicketService, tokenService, authorizationService);
    }

    /**
     * 注册自定义 OIDC Token 生成器 Bean。
     * <p>
     * 使用 {@link NimbusJwtEncoder} + id_token 订制器构建 {@link JwtGenerator}，
     * 然后将其与 {@link OidcTokenBridgeService} 组合为 {@link OidcTokenGenerator}。
     * </p>
     *
     * @param jwkSource   JWK 密钥源
     * @param bridgeService OIDC token 桥接服务
     * @return 自定义 token 生成器
     */
    @Bean
    public OidcTokenGenerator oidcTokenGenerator(
            com.nimbusds.jose.jwk.source.JWKSource<SecurityContext> jwkSource,
            OidcTokenBridgeService bridgeService) {
        NimbusJwtEncoder jwtEncoder = new NimbusJwtEncoder(jwkSource);
        JwtGenerator jwtGenerator = new JwtGenerator(jwtEncoder);
        jwtGenerator.setJwtCustomizer(oidcIdTokenCustomizer());
        return new OidcTokenGenerator(bridgeService, jwtGenerator);
    }

    /**
     * 注册混合式 {@link OAuth2AuthorizationService} Bean。
     * <p>
     * 使用内存委托处理 auth code 阶段的短生命周期授权状态，
     * 并在 refresh_token 缓存未命中时回退到遗留 token 验证逻辑。
     * </p>
     *
     * @param bridgeService              OIDC token 桥接服务
     * @param registeredClientRepository 注册客户端仓库
     * @return 混合式授权服务
     */
    @Bean
    public OAuth2AuthorizationService authorizationService(
            OidcTokenBridgeService bridgeService,
            RegisteredClientRepository registeredClientRepository,
            UserService userService) {
        return new OidcAuthorizationService(
                new InMemoryOAuth2AuthorizationService(),
                bridgeService,
                registeredClientRepository,
                userService
        );
    }

    /**
     * 注册 {@link OidcUserClaimsMapper} Bean。
     * <p>
     * 负责将系统用户数据按 scope 映射为 OIDC 标准声明，供 UserInfo 端点使用。
     * </p>
     *
     * @param userService 用户服务，用于按 uid 加载用户数据
     * @param property    OIDC 服务端配置属性，提供 Issuer 地址用于构建 picture URL
     * @return OIDC UserInfo 声明映射器
     */
    @Bean
    public OidcUserClaimsMapper oidcUserClaimsMapper(UserService userService, OidcServerProperty property) {
        return new OidcUserClaimsMapper(userService, property.getIssuer());
    }

    /**
     * 创建 id_token JWT 定制器。
     * <p>
     * 为 id_token 注入标准 OIDC 声明（sub 等），确保在授权码兑换与 refresh_token 刷新两种流程中
     * sub 声明始终为 uid 字符串，而非用户名。
     * </p>
     *
     * @return JWT 定制器
     */
    private OAuth2TokenCustomizer<JwtEncodingContext> oidcIdTokenCustomizer() {
        return context -> {
            if (context.getTokenType() != null &&
                    "id_token".equals(context.getTokenType().getValue())) {
                OidcUserInfo.Builder userInfoBuilder = OidcUserInfo.builder();
                // sub 由 Spring AS 自动填入 principalName（对我们而言 uid 字符串）
                context.getClaims().claims(claims -> {
                    // 保持 sub 不变；如需更多自定义声明可在此扩展
                });
            }
        };
    }
}
