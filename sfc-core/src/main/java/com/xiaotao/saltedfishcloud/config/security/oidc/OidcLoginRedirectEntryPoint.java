package com.xiaotao.saltedfishcloud.config.security.oidc;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;

/**
 * OIDC 登录重定向入口点，重定向时保留原始请求的查询参数。
 * <p>
 * 当未认证用户访问 {@code /oauth2/authorize?client_id=...&redirect_uri=...} 时，
 * 会重定向到 {@code /oauth?client_id=...&redirect_uri=...}，确保前端 SPA 能获取到完整的授权参数。
 * </p>
 */
public class OidcLoginRedirectEntryPoint implements AuthenticationEntryPoint {

    private final String loginUrl;

    public OidcLoginRedirectEntryPoint(String loginUrl) {
        this.loginUrl = loginUrl;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        String queryString = request.getQueryString();
        String redirectUrl = (queryString == null || queryString.isEmpty())
                ? loginUrl
                : loginUrl + "?" + queryString;
        response.sendRedirect(redirectUrl);
    }
}
