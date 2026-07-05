package com.xiaotao.saltedfishcloud.config.security;

import com.xiaotao.saltedfishcloud.dao.redis.TokenService;
import com.xiaotao.saltedfishcloud.model.json.JsonResultImpl;
import com.xiaotao.saltedfishcloud.model.po.LogRecord;
import com.xiaotao.saltedfishcloud.model.po.UserPrincipal;
import com.xiaotao.saltedfishcloud.service.log.LogLevel;
import com.xiaotao.saltedfishcloud.service.log.LogRecordManager;
import com.xiaotao.saltedfishcloud.service.user.UserService;
import com.xiaotao.saltedfishcloud.utils.MapperHolder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

import java.io.IOException;

/**
 * 在SpringSecurity过滤器链中处理用户登录的过滤器
 */
public class JwtLoginFilter extends AbstractAuthenticationProcessingFilter {
    private final static String LOGIN_LOG_TYPE = "用户登录";
    private final TokenService tokenDao;
    private final LogRecordManager logRecordManager;
    private final UserService userService;

    protected JwtLoginFilter(String defaultFilterProcessesUrl, AuthenticationManager authenticationManager, TokenService tokenDao, LogRecordManager logRecordManager, UserService userService) {
        super(PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, defaultFilterProcessesUrl));
        setAuthenticationManager(authenticationManager);
        this.tokenDao = tokenDao;
        this.logRecordManager = logRecordManager;
        this.userService = userService;
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
        UserPrincipal user = (UserPrincipal) authResult.getPrincipal();
        String token = tokenDao.generateUserToken(user);
        if ("1".equals(request.getParameter("getCookie"))) {
            response.setHeader("Set-Cookie",
                    String.format("token=%s; Path=/; HttpOnly; Secure; SameSite=Lax", token));
        }
        response.getWriter().print(JsonResultImpl.getInstance(token));
        userService.updateLoginDate(user.getId());
        logRecordManager.saveRecordAsync(LogRecord.builder()
                        .level(LogLevel.INFO)
                        .type(LOGIN_LOG_TYPE)
                        .msgAbstract("用户[" + user.getUsername() + "]登录成功 ip[" + request.getRemoteAddr() + "]")
                        .msgDetail(MapperHolder.toJsonNoEx(user))
                .build());
    }

    @Override
    protected void unsuccessfulAuthentication(HttpServletRequest request, HttpServletResponse response, AuthenticationException failed) throws IOException {
        response.setContentType("application/json;charset=utf-8");
        response.setStatus(400);
        response.getWriter().print(JsonResultImpl.getInstance(400, null, "用户名或密码错误"));
        logRecordManager.saveRecordAsync(LogRecord.builder()
                .level(LogLevel.ERROR)
                .type(LOGIN_LOG_TYPE)
                .msgAbstract("用户[" + request.getParameter("user") + "]登录失败 ip[" + request.getRemoteAddr() + "]")
                .msgDetail(failed.getMessage())
                .build());
    }
}
