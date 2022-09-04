package com.xiaotao.saltedfishcloud.config.security.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaotao.saltedfishcloud.model.json.JsonResultImpl;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class LoginFailedHandler implements AuthenticationFailureHandler {
    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception) throws IOException, ServletException {
        ObjectMapper mapper = new ObjectMapper();
        response.setCharacterEncoding("utf-8");
        response.setContentType("application/json;charset=utf-8");
        String res = mapper.writeValueAsString(JsonResultImpl.getInstance(0, null, "用户名或密码错误，登录失败"));
        response.getWriter().print(res);
    }
}
