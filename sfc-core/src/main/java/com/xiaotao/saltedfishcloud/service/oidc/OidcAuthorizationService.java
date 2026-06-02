package com.xiaotao.saltedfishcloud.service.oidc;

import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;

/**
 * OIDC 授权服务，直接委托给 JPA 持久化实现。
 * <p>
 * 所有 CRUD 操作均委托给 {@link JpaOAuth2AuthorizationService}。
 * </p>
 */
public class OidcAuthorizationService implements OAuth2AuthorizationService {

    private final OAuth2AuthorizationService delegate;

    /**
     * 构造授权服务。
     *
     * @param delegate 持久化授权委托（通常为 {@link JpaOAuth2AuthorizationService}）
     */
    public OidcAuthorizationService(OAuth2AuthorizationService delegate) {
        this.delegate = delegate;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void save(OAuth2Authorization authorization) {
        delegate.save(authorization);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void remove(OAuth2Authorization authorization) {
        delegate.remove(authorization);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public OAuth2Authorization findById(String id) {
        return delegate.findById(id);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public OAuth2Authorization findByToken(String token, OAuth2TokenType tokenType) {
        return delegate.findByToken(token, tokenType);
    }
}
