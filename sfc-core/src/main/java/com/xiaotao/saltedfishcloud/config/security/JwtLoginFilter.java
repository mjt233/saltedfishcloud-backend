package com.xiaotao.saltedfishcloud.config.security;

import com.xiaotao.saltedfishcloud.dao.redis.TokenServiceImpl;
import com.xiaotao.saltedfishcloud.model.json.JsonResultImpl;
import com.xiaotao.saltedfishcloud.model.po.LogRecord;
import com.xiaotao.saltedfishcloud.model.po.User;
import com.xiaotao.saltedfishcloud.service.log.LogLevel;
import com.xiaotao.saltedfishcloud.service.log.LogRecordManager;
import com.xiaotao.saltedfishcloud.service.user.UserService;
import com.xiaotao.saltedfishcloud.utils.MapperHolder;
import lombok.Setter;
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
import java.util.Optional;

/**
 * 在SpringSecurity过滤器链中处理用户登录的过滤器
 */
public class JwtLoginFilter extends AbstractAuthenticationProcessingFilter {
    private final static String LOGIN_LOG_TYPE = "用户登录";
    private final TokenServiceImpl tokenDao;
    @Setter
    private LogRecordManager logRecordManager;

    @Setter
    private UserService userService;

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
        userService.updateLoginDate(user.getId());
        logRecordManager.saveRecordAsync(LogRecord.builder()
                        .level(LogLevel.INFO)
                        .type(LOGIN_LOG_TYPE)
                        .msgAbstract("用户[" + user.getUser() + "]登录成功 ip[" + request.getRemoteAddr() + "]")
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
