package com.xiaotao.saltedfishcloud.config.security.oidc;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import com.xiaotao.saltedfishcloud.config.oidc.OidcServerProperty;
import com.xiaotao.saltedfishcloud.config.security.JwtAuthenticationFilter;
import com.xiaotao.saltedfishcloud.dao.jpa.Oauth2AuthorizationRepo;
import com.xiaotao.saltedfishcloud.dao.jpa.ThirdPartyAppKeyRepo;
import com.xiaotao.saltedfishcloud.service.oidc.JpaOAuth2AuthorizationService;
import com.xiaotao.saltedfishcloud.service.oidc.OidcAuthorizationConsentService;
import com.xiaotao.saltedfishcloud.service.oidc.OidcAuthorizationService;
import com.xiaotao.saltedfishcloud.service.oidc.OidcRegisteredClientRepository;
import com.xiaotao.saltedfishcloud.service.oidc.OidcUserClaimsMapper;
import com.xiaotao.saltedfishcloud.service.third.ThirdPartyAppAuthorizationService;
import com.xiaotao.saltedfishcloud.service.third.ThirdPartyAppService;
import com.xiaotao.saltedfishcloud.service.user.UserService;
import com.xiaotao.saltedfishcloud.utils.SecureUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.authentication.ClientSecretAuthenticationProvider;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
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
 *   <li>{@link OAuth2AuthorizationService} → {@link OidcAuthorizationService}（JPA 持久化授权服务）</li>
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
                                                       OidcServerProperty property,
                                                       OidcUserClaimsMapper claimsMapper,
                                                       AuthorizationServerSettings settings,
                                                       JwtAuthenticationFilter jwtAuthenticationFilter,
                                                       AuthenticationProvider authenticationProvider) throws Exception {
        OAuth2AuthorizationServerConfigurer authorizationServer =
                OAuth2AuthorizationServerConfigurer.authorizationServer();
        http.with(authorizationServer, server -> server
                        .authorizationServerSettings(settings)
                        .authorizationEndpoint(authorizationEndpoint ->
                                authorizationEndpoint.consentPage(property.getConsentPage()))
                        .oidc(oidc -> oidc.userInfoEndpoint(e -> e.userInfoMapper(claimsMapper::toOidcUserInfo)))
                        .deviceAuthorizationEndpoint(deviceAuthorization ->
                                deviceAuthorization.verificationUri(property.getDeviceConsentPage()))
                        .deviceVerificationEndpoint(deviceVerification -> {
                            deviceVerification.consentPage(property.getDeviceConsentPage());
                            deviceVerification.deviceVerificationResponseHandler(
                                    new SimpleUrlAuthenticationSuccessHandler(property.getDeviceVerificationSuccessUri()));
                            deviceVerification.errorResponseHandler(
                                    new SimpleUrlAuthenticationFailureHandler(property.getDeviceVerificationErrorUri()));
                        }))
                .addFilterBefore(jwtAuthenticationFilter, AbstractPreAuthenticatedProcessingFilter.class)
                .authenticationProvider(authenticationProvider)
                .securityMatcher(authorizationServer.getEndpointsMatcher())
                .oauth2ResourceServer(Customizer.withDefaults())
                .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
                .csrf(csrf -> csrf.ignoringRequestMatchers(authorizationServer.getEndpointsMatcher()))
                .exceptionHandling(ex -> ex.defaultAuthenticationEntryPointFor(
                        new OidcLoginRedirectEntryPoint(property.getConsentPage()),
                        new MediaTypeRequestMatcher(MediaType.TEXT_HTML, MediaType.APPLICATION_XHTML_XML)
                ));;
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
     * 注册 {@link OidcAuthorizationService} Bean。
     * <p>
     * 直接委托给 JPA 持久化实现。
     * </p>
     *
     * @param registeredClientRepository 注册客户端仓库
     * @param repo                       OAuth2 授权持久化仓库
     * @return 授权服务实例
     */
    @Bean
    public OAuth2AuthorizationService authorizationService(
            RegisteredClientRepository registeredClientRepository,
            Oauth2AuthorizationRepo repo) {
        return new OidcAuthorizationService(
                new JpaOAuth2AuthorizationService(repo, registeredClientRepository)
        );
    }

}
