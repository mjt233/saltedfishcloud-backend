package com.sfc.cloud.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;

/**
 * {@link RequireInternalToken} 注解的作用域校验拦截器。
 * <p>
 * 只做一件事：检查当前请求的令牌是否具备端点上声明的全部作用域。
 * 令牌<b>有效但作用域不足</b>时返回 <b>403</b>——与 core 侧
 * {@code RequireScopeAspect} 抛 {@code AccessDeniedException} 的语义对齐
 * （401 是"未认证"，403 是"已认证但无权限"，两者不能混用）。
 * </p>
 * <p>
 * 本类<b>不负责</b>"是否鉴权"：那是 {@link InternalTokenAuthFilter} 的职责，且是 fail-closed 的。
 * 因此拦截器在拿不到令牌时返回 401 只是防御性兜底——正常情况下无令牌的请求
 * 根本到不了 Controller。
 * </p>
 */
@Slf4j
public class RequireInternalTokenInterceptor implements HandlerInterceptor {

    private final ServiceAuthClientProperties properties;

    /**
     * 构造作用域校验拦截器。
     *
     * @param properties 微服务侧验签配置，用于判断功能是否被显式关闭
     */
    public RequireInternalTokenInterceptor(ServiceAuthClientProperties properties) {
        this.properties = properties;
    }

    /**
     * 在方法执行前校验 {@link RequireInternalToken} 声明的作用域。
     *
     * @param request  HTTP 请求
     * @param response HTTP 响应
     * @param handler  处理器
     * @return 校验通过时返回 {@code true}
     * @throws java.io.IOException 写出错误响应失败时抛出
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws java.io.IOException {
        if (!properties.isEnabled()) {
            return true;
        }
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        RequireInternalToken annotation = handlerMethod.getMethodAnnotation(RequireInternalToken.class);
        if (annotation == null) {
            annotation = handlerMethod.getBeanType().getAnnotation(RequireInternalToken.class);
        }
        if (annotation == null) {
            return true;
        }

        InternalToken token = InternalTokenContext.get(request);
        if (token == null) {
            // 防御性兜底：fail-closed 的过滤器本应已经拦下无令牌请求
            ErrorResponses.write(response, 401, "缺少内部服务凭证");
            return false;
        }

        for (String required : annotation.value()) {
            if (!token.hasScope(required)) {
                log.debug("内部服务令牌作用域不足 uri={} 需要={} 实际={}",
                        request.getRequestURI(), Arrays.toString(annotation.value()), token.scopes());
                ErrorResponses.write(response, 403, "内部服务凭证权限不足，缺少作用域: " + required);
                return false;
            }
        }
        return true;
    }
}
