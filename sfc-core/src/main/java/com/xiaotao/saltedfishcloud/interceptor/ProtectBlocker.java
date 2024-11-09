package com.xiaotao.saltedfishcloud.interceptor;

import com.xiaotao.saltedfishcloud.annotations.NotBlock;
import com.xiaotao.saltedfishcloud.annotations.ProtectBlock;
import com.xiaotao.saltedfishcloud.config.SysRuntimeConfig;
import com.xiaotao.saltedfishcloud.model.json.JsonResultImpl;
import com.xiaotao.saltedfishcloud.enums.ProtectLevel;
import com.xiaotao.saltedfishcloud.utils.ArrayUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ProtectBlocker implements HandlerInterceptor {
    @Autowired
    private SysRuntimeConfig sysRuntimeConfig;

    private final Map<Method, Boolean> CACHE_MAP = new ConcurrentHashMap<>();

    /**
     * 检查是否处于存储模式的切换中状态，如果是，将阻止受影响的控制器的执行
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        ProtectLevel level = sysRuntimeConfig.getProtectModeLevel();
        if (level == null || level == ProtectLevel.OFF) {
            return true;
        }

        if (handler instanceof HandlerMethod) {
            HandlerMethod method = (HandlerMethod) handler;
            Boolean canBlock = CACHE_MAP.get(method.getMethod());
            if (canBlock == null) {

                // 读取注解是否需要因为保护级别而阻止方法并缓存结果
                ProtectBlock block = method.getMethod().getAnnotation(ProtectBlock.class);
                NotBlock notBlock = method.getMethod().getAnnotation(NotBlock.class);
                if (block == null) block = method.getBeanType().getAnnotation(ProtectBlock.class);
                if ( block != null && ArrayUtils.contain(block.level(), level) && (notBlock == null || !ArrayUtils.contain(notBlock.level(), level)) ) {
                    CACHE_MAP.put(method.getMethod(), true);
                    canBlock = true;
                } else {
                    CACHE_MAP.put(method.getMethod(), false);
                    canBlock = false;
                }
            }

            if (canBlock) {
                response.setContentType("application/json;charset=utf-8");
                response.getWriter().print(JsonResultImpl.getInstance(501, null, "系统处于保护状态，暂时无法响应该API的请求，稍后将解除，请过段时间再试").toString());
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
