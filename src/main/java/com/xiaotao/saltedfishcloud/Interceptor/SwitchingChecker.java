package com.xiaotao.saltedfishcloud.Interceptor;

import com.xiaotao.saltedfishcloud.annotations.BlockWhileSwitching;
import com.xiaotao.saltedfishcloud.config.DiskConfig;
import com.xiaotao.saltedfishcloud.po.JsonResult;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@Component
public class SwitchingChecker implements HandlerInterceptor {
    /**
     * 检查是否处于存储模式的切换中状态，如果是，将阻止受影响的控制器的执行
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        if (handler instanceof HandlerMethod) {
            HandlerMethod method = (HandlerMethod) handler;
            BlockWhileSwitching annotation = method.getMethod().getAnnotation(BlockWhileSwitching.class);
            if (DiskConfig.isStoreSwitching() && (annotation != null || method.getBeanType().getAnnotation(BlockWhileSwitching.class) != null) ){
                response.setContentType("application/json;charset=utf-8");
                response.getWriter().print(JsonResult.getInstance(501, null, "系统存储切换中，暂时无法响应该API的请求，请稍后重试").toString());
                return false;
            }
        }
        return true;
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) throws Exception {

    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {

    }
}
