package com.xiaotao.saltedfishcloud.config.security;

import com.xiaotao.saltedfishcloud.dao.redis.TokenService;
import com.xiaotao.saltedfishcloud.model.po.UserPrincipal;
import com.xiaotao.saltedfishcloud.utils.JwtUtils;
import com.xiaotao.saltedfishcloud.utils.MapperHolder;
import com.xiaotao.saltedfishcloud.utils.SecureUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT 认证过滤器，从请求中提取 JWT token 并设置认证信息。
 * <p>
 * 通过 {@link SecureUtils#getToken(HttpServletRequest)} 获取 token，
 * 支持 Authorization header、表单参数和 Cookie 三种来源。
 * </p>
 * <p>
 * 该过滤器被多个安全过滤器链复用（主链、OIDC 链等）。
 * </p>
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final TokenService tokenService;

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        String token = SecureUtils.getToken(req);
        if (token == null || token.isEmpty()) {
            chain.doFilter(req, response);
            return;
        }
        try {
            UserPrincipal user = MapperHolder.mapper.readValue(JwtUtils.parse(token), UserPrincipal.class);
            user.setToken(token);
            if (tokenService.isTokenValid(user.getId(), token)) {
                SecurityContextHolder.getContext()
                        .setAuthentication(new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
            }
        } catch (Exception ignored) {
        }
        chain.doFilter(req, response);
    }
}
