package com.xiaotao.saltedfishcloud.annotations;

import com.xiaotao.saltedfishcloud.constant.StandardScopes;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 开放平台授权范围检查注解。
 * <p>
 * 用于 Controller 方法上，指定调用该方法所需的 OAuth2 Scope 权限。
 * 会在方法执行前检查当前认证信息中是否包含 {@code SCOPE_<value>} 权限。
 * 若缺少权限则抛出 {@link org.springframework.security.access.AccessDeniedException}。
 * </p>
 *
 * @see StandardScopes
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequireScope {

    /**
     * 所需的 Scope 名称（小写），例如 {@code "storage_read"}、{@code "profile"}。
     * 系统会自动拼接 {@code "SCOPE_"} 前缀后进行权限检查。
     */
    String value();
}
