package com.xiaotao.saltedfishcloud.Interceptor;

import com.xiaotao.saltedfishcloud.annotations.ReadOnlyBlock;
import com.xiaotao.saltedfishcloud.annotations.NotBlock;
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
public class ReadOnlyBlocker implements HandlerInterceptor {
    /**
     * 检查是否处于存储模式的切换中状态，如果是，将阻止受影响的控制器的执行
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        if (handler instanceof HandlerMethod) {
            HandlerMethod method = (HandlerMethod) handler;
            ReadOnlyBlock annotation = method.getMethod().getAnnotation(ReadOnlyBlock.class);
            if (    DiskConfig.isReadOnlyBlock() &&
                    (annotation != null || method.getBeanType().getAnnotation(ReadOnlyBlock.class) != null) &&
                    method.getMethod().getAnnotation(NotBlock.class) == null
            ){
                response.setContentType("application/json;charset=utf-8");
                response.getWriter().print(JsonResult.getInstance(501, null, "系统处于只读状态，暂时无法响应该API的请求，稍后将解除，请过段时间再试").toString());
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
