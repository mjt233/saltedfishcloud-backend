package com.xiaotao.saltedfishcloud.config.security;

import com.xiaotao.saltedfishcloud.model.po.User;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;

public class OidcAuthenticationToken extends AbstractAuthenticationToken {
    private final User user;
    private final String apiTicket;
    public OidcAuthenticationToken(User user, String apiTicket, Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.user = user;
        this.apiTicket = apiTicket;
    }

    @Override
    public Object getCredentials() {
        return this.apiTicket;
    }

    @Override
    public Object getPrincipal() {
        return user;
    }
}
