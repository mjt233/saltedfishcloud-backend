package com.sfc.mcp.security;

import com.sfc.mcp.service.McpApiKeyService;
import com.xiaotao.saltedfishcloud.constant.SysRole;
import com.xiaotao.saltedfishcloud.model.po.User;
import com.xiaotao.saltedfishcloud.model.po.UserPrincipal;
import com.xiaotao.saltedfishcloud.service.user.UserService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * MCP API Key 认证过滤器。
 * <p>
 * 拦截 {@code Authorization: Bearer sfc_mcp_*} 格式的请求，
 * 验证 API Key 并设置 Spring Security 上下文，授予 {@code ROLE_OAUTH_USER} 角色
 * 以及 {@code SCOPE_storage_read} 和 {@code SCOPE_storage_write} 权限。
 * 该过滤器使 MCP 客户端和 OpenAPI 端点（上传/下载）都能使用同一 token 认证。
 */
public class McpApiKeyFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String TOKEN_PREFIX = "sfc_mcp_";

    private final McpApiKeyService mcpApiKeyService;
    private final UserService userService;

    public McpApiKeyFilter(McpApiKeyService mcpApiKeyService, UserService userService) {
        this.mcpApiKeyService = mcpApiKeyService;
        this.userService = userService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authorization.substring(BEARER_PREFIX.length());
        if (!token.startsWith(TOKEN_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        Long uid = mcpApiKeyService.validate(token);
        if (uid == null) {
            filterChain.doFilter(request, response);
            return;
        }

        User user = userService.getUserById(uid);
        if (user == null) {
            filterChain.doFilter(request, response);
            return;
        }

        UserPrincipal principal = UserPrincipal.from(user);
        List<GrantedAuthority> authorities = new ArrayList<>(principal.getAuthorities());
        authorities.add(new SimpleGrantedAuthority("ROLE_" + SysRole.OAUTH_USER));
        authorities.add(new SimpleGrantedAuthority("SCOPE_storage_read"));
        authorities.add(new SimpleGrantedAuthority("SCOPE_storage_write"));

        UsernamePasswordAuthenticationToken authenticationToken =
                new UsernamePasswordAuthenticationToken(principal, token, authorities);
        SecurityContextHolder.getContext().setAuthentication(authenticationToken);
        filterChain.doFilter(request, response);
    }
}
