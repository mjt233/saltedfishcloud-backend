package com.xiaotao.saltedfishcloud.config.security.oidc;

import com.xiaotao.saltedfishcloud.dao.jpa.ThirdPartyAppKeyRepo;
import com.xiaotao.saltedfishcloud.model.po.ThirdPartyAppKey;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;

import java.util.List;

/**
 * 支持多客户端密钥的 {@link AuthenticationProvider} 实现。
 * <p>
 * 当第三方应用有多个 {@link ThirdPartyAppKey} 时，逐一验证客户端提供的密钥原文
 * 是否与任意一个密钥的哈希值匹配。匹配任一即通过认证。
 * 解决 {@code OidcRegisteredClientRepository} 只能使用第一个密钥的问题。
 * </p>
 */
public class MultiKeyClientSecretAuthenticationProvider implements AuthenticationProvider {

    private final RegisteredClientRepository registeredClientRepository;
    private final ThirdPartyAppKeyRepo keyRepo;
    private final PasswordEncoder passwordEncoder;

    /**
     * 创建多密钥客户端认证提供者。
     *
     * @param registeredClientRepository 注册客户端仓库
     * @param keyRepo                    第三方应用密钥仓库
     * @param passwordEncoder            密码编码器，用于验证客户端密钥
     */
    public MultiKeyClientSecretAuthenticationProvider(
            RegisteredClientRepository registeredClientRepository,
            ThirdPartyAppKeyRepo keyRepo,
            PasswordEncoder passwordEncoder) {
        this.registeredClientRepository = registeredClientRepository;
        this.keyRepo = keyRepo;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        OAuth2ClientAuthenticationToken clientAuth = (OAuth2ClientAuthenticationToken) authentication;

        if (!ClientAuthenticationMethod.CLIENT_SECRET_BASIC.equals(clientAuth.getClientAuthenticationMethod())
                && !ClientAuthenticationMethod.CLIENT_SECRET_POST.equals(clientAuth.getClientAuthenticationMethod())) {
            return null;
        }

        String clientId = clientAuth.getPrincipal().toString();
        RegisteredClient registeredClient = this.registeredClientRepository.findByClientId(clientId);
        if (registeredClient == null) {
            return null;
        }

        if (!registeredClient.getClientAuthenticationMethods().contains(clientAuth.getClientAuthenticationMethod())) {
            return null;
        }

        String rawSecret = clientAuth.getCredentials().toString();
        List<ThirdPartyAppKey> keys = keyRepo.findByAppId(Long.valueOf(clientId));

        boolean matched = keys.stream()
                .map(ThirdPartyAppKey::getClientSecretHash)
                .anyMatch(hash -> passwordEncoder.matches(rawSecret, hash));

        if (!matched) {
            throw new BadCredentialsException("invalid_client");
        }

        return new OAuth2ClientAuthenticationToken(
                registeredClient, clientAuth.getClientAuthenticationMethod(), rawSecret);
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return OAuth2ClientAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
