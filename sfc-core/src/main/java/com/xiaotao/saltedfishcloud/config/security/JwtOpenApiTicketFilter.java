package com.xiaotao.saltedfishcloud.config.security;

import com.xiaotao.saltedfishcloud.constant.SysRole;
import com.xiaotao.saltedfishcloud.model.po.User;
import com.xiaotao.saltedfishcloud.model.po.UserPrincipal;
import com.xiaotao.saltedfishcloud.model.vo.ThirdPartyAppApiTicketPayload;
import com.xiaotao.saltedfishcloud.service.third.ThirdPartyAppTokenService;
import com.xiaotao.saltedfishcloud.service.user.UserService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class JwtOpenApiTicketFilter extends OncePerRequestFilter {
    private final ThirdPartyAppTokenService thirdPartyAppTokenService;
    private final UserService userService;

    public JwtOpenApiTicketFilter(ThirdPartyAppTokenService thirdPartyAppTokenService, UserService userService) {
        this.thirdPartyAppTokenService = thirdPartyAppTokenService;
        this.userService = userService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith("ApiTicket ")) {
            filterChain.doFilter(request, response);
            return;
        }
        String apiTicket = authorization.substring(10);
        ThirdPartyAppApiTicketPayload apiTicketPayload = thirdPartyAppTokenService.parseAndValidateApiTicket(apiTicket);
        Long uid = apiTicketPayload.getUid();
        User user = userService.getUserById(uid);
        UserPrincipal principal = UserPrincipal.from(user);
        List<GrantedAuthority> authorities = new ArrayList<>(principal.getAuthorities());
        authorities.add(new SimpleGrantedAuthority("ROLE_" + SysRole.OAUTH_USER));
        Arrays.stream(apiTicketPayload.getScope().split(" "))
                .filter(s -> !s.isEmpty())
                .forEach(scope -> authorities.add(new SimpleGrantedAuthority("SCOPE_" + scope)));


        OidcAuthenticationToken authenticationToken = new OidcAuthenticationToken(principal, apiTicket, authorities);
        authenticationToken.setAuthenticated(true);
        authenticationToken.setDetails(apiTicketPayload);
        SecurityContextHolder.getContext().setAuthentication(authenticationToken);
        filterChain.doFilter(request, response);
    }
}
