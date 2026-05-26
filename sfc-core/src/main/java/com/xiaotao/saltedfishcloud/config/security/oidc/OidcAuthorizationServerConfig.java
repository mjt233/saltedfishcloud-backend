package com.xiaotao.saltedfishcloud.config.security.oidc;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import com.xiaotao.saltedfishcloud.config.oidc.OidcServerProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;

/**
 * OIDC 授权服务器安全配置类。
 * <p>
 * 注册 OAuth2/OIDC 授权服务器所需的核心 Bean：
 * <ul>
 *   <li>{@link SecurityFilterChain}（最高优先级，仅拦截 OAuth2/OIDC 标准端点）</li>
 *   <li>{@link AuthorizationServerSettings}（Issuer 地址与各端点路径）</li>
 *   <li>{@link com.nimbusds.jose.jwk.source.JWKSource}（JWT 签名与 JWKS 端点密钥源）</li>
 * </ul>
 * 该过滤器链与应用主过滤器链（{@code SecurityConfig.filterChain}）共存：
 * 本链通过 {@code securityMatcher} 仅匹配授权服务器端点，主链作为低优先级后备处理其余请求。
 * </p>
 */
@Configuration
@ConditionalOnProperty(name = "sys.oidc.enabled", havingValue = "true")
public class OidcAuthorizationServerConfig {

    /**
     * 配置 OIDC 授权服务器安全过滤器链。
     * <p>
     * 仅匹配授权服务器标准端点（{@code /oauth2/**}、{@code /.well-known/**} 等），
     * 对来自浏览器的未认证请求重定向至 {@code /oauth} 登录页。
     * 优先级设为 {@link Ordered#HIGHEST_PRECEDENCE}，确保在应用主链之前处理 OIDC 请求。
     * </p>
     *
     * @param http Spring Security {@link HttpSecurity} 构建器
     * @return 授权服务器安全过滤器链
     * @throws Exception 如果构建过滤器链失败
     */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public SecurityFilterChain oidcSecurityFilterChain(HttpSecurity http) throws Exception {
        OAuth2AuthorizationServerConfigurer authorizationServer =
                OAuth2AuthorizationServerConfigurer.authorizationServer();
        http.securityMatcher(authorizationServer.getEndpointsMatcher())
                .with(authorizationServer, server -> server.oidc(Customizer.withDefaults()))
                .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
                .exceptionHandling(ex -> ex.defaultAuthenticationEntryPointFor(
                        new LoginUrlAuthenticationEntryPoint("/oauth"),
                        new MediaTypeRequestMatcher(MediaType.TEXT_HTML)
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
}
