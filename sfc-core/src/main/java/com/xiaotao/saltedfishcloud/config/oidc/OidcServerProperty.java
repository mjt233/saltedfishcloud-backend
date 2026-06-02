package com.xiaotao.saltedfishcloud.config.oidc;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * OIDC 服务端配置属性。
 * 绑定配置前缀 {@code sys.oidc}，用于控制内置 OIDC/OAuth2 授权服务器的行为。
 */
@Data
@ConfigurationProperties(prefix = "sys.oidc")
public class OidcServerProperty {

    /**
     * 验证配置的合法性。
     * <p>
     * 当 {@code enabled=false} 时，跳过所有校验。
     * </p>
     */
    public void validate() {
    }

    /**
     * 是否启用内置 OIDC 授权服务器，默认 {@code false}。
     */
    private boolean enabled = false;

    /**
     * OAuth2 授权端点路径，默认 {@code /oauth2/authorize}。
     */
    private String authorizationEndpoint = "/oauth2/authorize";

    /**
     * OAuth2 令牌端点路径，默认 {@code /oauth2/token}。
     */
    private String tokenEndpoint = "/oauth2/token";

    /**
     * OAuth2 设备授权端点路径，默认 {@code /oauth2/device_authorization}。
     */
    private String deviceAuthorizationEndpoint = "/oauth2/device_authorization";

    /**
     * OAuth2 设备核验端点路径，默认 {@code /oauth2/device_verification}。
     */
    private String deviceVerificationEndpoint = "/oauth2/device_verification";

    /**
     * OIDC 用户信息端点路径，默认 {@code /oauth2/userinfo}。
     */
    private String userInfoEndpoint = "/oauth2/userinfo";

    /**
     * JWK Set 端点路径，默认 {@code /oauth2/jwks}。
     */
    private String jwkSetEndpoint = "/oauth2/jwks";

    /**
     * 令牌吊销端点路径，默认 {@code /oauth2/revoke}。
     */
    private String revocationEndpoint = "/oauth2/revoke";

    /**
     * 令牌内省端点路径，默认 {@code /oauth2/introspect}。
     */
    private String introspectionEndpoint = "/oauth2/introspect";

    /**
     * OIDC 退出端点路径，默认 {@code /connect/logout}。
     */
    private String logoutEndpoint = "/connect/logout";

    /**
     * OAuth2 授权确认页面路径，默认 {@code /oauth}。
     */
    private String consentPage = "/oauth";

    /**
     * 设备授权确认页面路径，默认 {@code /oauth?grant_type=user_code}。
     */
    private String deviceConsentPage = "/oauth?grant_type=user_code";

    /**
     * 设备授权成功后重定向地址，默认 {@code /oauth?grant_type=user_code&success=true}。
     */
    private String deviceVerificationSuccessUri = "/oauth?grant_type=user_code&success=true";

    /**
     * 设备授权失败后重定向地址，默认 {@code /oauth?grant_type=user_code&error=invalid_user_code}。
     */
    private String deviceVerificationErrorUri = "/oauth?grant_type=user_code&error=invalid_user_code";

    /**
     * JWK（JSON Web Key）相关配置。
     */
    private final Jwk jwk = new Jwk();

    /**
     * JWK 密钥配置。
     */
    @Data
    public static class Jwk {

        /**
         * JWK 密钥 ID，默认 {@code oidc-key-1}。
         */
        private String keyId = "oidc-key-1";

        /**
         * JWK 密钥存储文件路径，默认 {@code ./oidc-jwk.json}。
         */
        private String keyStorePath = "./oidc-jwk.json";
    }
}
