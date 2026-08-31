package com.xiaotao.saltedfishcloud.config.security.oidc;

import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.util.Assert;

/**
 * 认证 public client（{@link ClientAuthenticationMethod#NONE}）在设备授权流与
 * refresh_token 刷新场景下的客户端身份。
 * <p>
 * 与框架内置 {@code PublicClientAuthenticationProvider} 的差异：
 * 内置实现会强制执行面向授权码的 PKCE {@code code_verifier} 校验，
 * 不适用于设备码申请、设备码换票与 refresh_token。
 * 本 Provider 仅在非授权码场景下处理 {@code NONE}，授权码 + PKCE 仍交给内置实现。
 * </p>
 *
 * @see PublicClientDeviceAuthenticationConverter
 */
public final class PublicClientDeviceAuthenticationProvider implements AuthenticationProvider {

    private static final String ERROR_URI = "https://datatracker.ietf.org/doc/html/rfc6749#section-3.2.1";

    private final RegisteredClientRepository registeredClientRepository;

    /**
     * 创建 public client 设备流认证提供者。
     *
     * @param registeredClientRepository 注册客户端仓库
     */
    public PublicClientDeviceAuthenticationProvider(RegisteredClientRepository registeredClientRepository) {
        Assert.notNull(registeredClientRepository, "registeredClientRepository cannot be null");
        this.registeredClientRepository = registeredClientRepository;
    }

    /**
     * 认证 public client；非本 Provider 职责时返回 {@code null}。
     *
     * @param authentication 客户端认证令牌
     * @return 已认证的 {@link OAuth2ClientAuthenticationToken}，或不处理时返回 {@code null}
     * @throws AuthenticationException 客户端无效或不允许使用 NONE 时
     */
    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        OAuth2ClientAuthenticationToken clientAuth = (OAuth2ClientAuthenticationToken) authentication;

        // 仅处理 none 方法
        if (!ClientAuthenticationMethod.NONE.equals(clientAuth.getClientAuthenticationMethod())) {
            return null;
        }

        // 授权码 + PKCE 交给框架 PublicClientAuthenticationProvider
        Object grantType = clientAuth.getAdditionalParameters().get(OAuth2ParameterNames.GRANT_TYPE);
        if (AuthorizationGrantType.AUTHORIZATION_CODE.getValue().equals(grantType)) {
            return null;
        }

        String clientId = clientAuth.getPrincipal().toString();
        RegisteredClient registeredClient = this.registeredClientRepository.findByClientId(clientId);
        if (registeredClient == null) {
            throw invalidClient("client_id");
        }
        if (!registeredClient.getClientAuthenticationMethods().contains(ClientAuthenticationMethod.NONE)) {
            throw invalidClient("authentication_method");
        }

        return new OAuth2ClientAuthenticationToken(
                registeredClient,
                ClientAuthenticationMethod.NONE,
                null
        );
    }

    /**
     * 是否支持给定的认证类型。
     *
     * @param authentication 认证类型
     * @return 仅支持 {@link OAuth2ClientAuthenticationToken}
     */
    @Override
    public boolean supports(Class<?> authentication) {
        return OAuth2ClientAuthenticationToken.class.isAssignableFrom(authentication);
    }

    /**
     * 构造标准 {@code invalid_client} 异常。
     *
     * @param description 错误描述中附加的字段名
     * @return OAuth2 认证异常
     */
    private static OAuth2AuthenticationException invalidClient(String description) {
        OAuth2Error error = new OAuth2Error(
                OAuth2ErrorCodes.INVALID_CLIENT,
                "Client authentication failed: " + description,
                ERROR_URI
        );
        return new OAuth2AuthenticationException(error);
    }
}
