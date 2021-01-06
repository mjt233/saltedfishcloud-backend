package com.xiaotao.saltedfishcloud.Interceptor;

import com.xiaotao.saltedfishcloud.exception.HasResultException;
import com.xiaotao.saltedfishcloud.po.User;
import com.xiaotao.saltedfishcloud.service.UserService;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.resource.ResourceHttpRequestHandler;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Arrays;

@Component
public class LoginInterceptor implements HandlerInterceptor {
    @Resource
    UserService userService;
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws HasResultException {
        if(handler instanceof ResourceHttpRequestHandler) return true;
        final String[] token = {null};
        try {
            token[0] = request.getParameter("token");
            token[0] = token[0] == null ? request.getHeader("token") : token[0];
            if (token[0] == null)
                Arrays.stream(request.getCookies()).filter(cookie -> "token".equals(cookie.getName())).forEach(cookie -> token[0] = cookie.getValue());
            User user = userService.getUserByToken(token[0]);
            if (null != user) {
                request.setAttribute("user", user);
                return true;
            } else {
                throw new NullPointerException();
            }
        } catch (NullPointerException e) {
            throw new HasResultException("未登录");
        }
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) throws Exception {

    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {

    }
}
