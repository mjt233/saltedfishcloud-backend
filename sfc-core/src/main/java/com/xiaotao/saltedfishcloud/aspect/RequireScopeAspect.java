package com.xiaotao.saltedfishcloud.aspect;

import com.xiaotao.saltedfishcloud.annotations.RequireScope;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * 处理 {@link RequireScope} 注解的切面。
 * <p>
 * 在方法执行前检查当前认证信息中是否包含对应的 {@code SCOPE_<value>} 权限，
 * 若缺少权限则抛出 {@link AccessDeniedException}。
 * </p>
 */
@Component
@Aspect
public class RequireScopeAspect {

    /**
     * 检查 {@link RequireScope} 注解指定的 Scope 权限。
     *
     * @param pjp          连接点
     * @param requireScope 注解实例
     * @return 方法执行结果
     * @throws Throwable 方法执行异常或权限不足
     */
    @Around("@annotation(requireScope)")
    public Object checkScope(ProceedingJoinPoint pjp, RequireScope requireScope) throws Throwable {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String required = "SCOPE_" + requireScope.value();

        if (auth == null || auth.getAuthorities().stream()
                .noneMatch(grantedAuthority -> grantedAuthority.getAuthority().equals(required))) {
            throw new AccessDeniedException("缺少权限: " + requireScope.value());
        }
        return pjp.proceed();
    }
}
