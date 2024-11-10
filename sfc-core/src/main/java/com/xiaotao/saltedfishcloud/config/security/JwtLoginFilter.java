package com.xiaotao.saltedfishcloud.config.security;

import com.xiaotao.saltedfishcloud.dao.redis.TokenServiceImpl;
import com.xiaotao.saltedfishcloud.model.json.JsonResultImpl;
import com.xiaotao.saltedfishcloud.model.po.User;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * 在SpringSecurity过滤器链中处理用户登录的过滤器
 */
public class JwtLoginFilter extends AbstractAuthenticationProcessingFilter {
    private final TokenServiceImpl tokenDao;

    protected JwtLoginFilter(String defaultFilterProcessesUrl, AuthenticationManager authenticationManager, TokenServiceImpl tokenDao) {
        super(new AntPathRequestMatcher(defaultFilterProcessesUrl));
        setAuthenticationManager(authenticationManager);
        this.tokenDao = tokenDao;
    }

    @Override
    public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response) throws AuthenticationException {
        String user = request.getParameter("user");
        String passwd = request.getParameter("passwd");
        UsernamePasswordAuthenticationToken token = new UsernamePasswordAuthenticationToken(user, passwd);
        return getAuthenticationManager().authenticate(token);
    }

    @Override
    protected void successfulAuthentication(HttpServletRequest request, HttpServletResponse response, FilterChain chain, Authentication authResult) throws IOException {
        response.setContentType("application/json;charset=utf-8");
        User user = (User)authResult.getPrincipal();
        String token = tokenDao.generateUserToken(user);
        response.getWriter().print(JsonResultImpl.getInstance(token));
    }

    @Override
    protected void unsuccessfulAuthentication(HttpServletRequest request, HttpServletResponse response, AuthenticationException failed) throws IOException {
        response.setContentType("application/json;charset=utf-8");
        response.setStatus(400);
        response.getWriter().print(JsonResultImpl.getInstance(400, null, "用户名或密码错误"));
    }
}
