package com.sfc.cloud.data.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 验证当前登录用户是否具有操作指定 uid 资源的权限。
 *
 * <p>迁移自宿主的 {@code com.xiaotao.saltedfishcloud.validator.annotations.UID}，
 * 校验逻辑改由身份 SPI（{@link com.sfc.cloud.data.identity.CurrentUserProvider}）驱动，
 * 使宿主与微服务两侧可复用同一套判定口径。</p>
 */
@Documented
@Target({ElementType.PARAMETER, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = SfcUIDValidator.class)
public @interface SfcUID {

    /**
     * uid 为 0 的公共资源是否仅允许管理员操作。
     *
     * @return 仅允许管理员时返回 {@code true}
     */
    boolean value() default false;

    /**
     * 校验失败时的提示消息。
     *
     * @return 提示消息
     */
    String message() default "无权操作资源";

    /**
     * Bean Validation 分组。
     *
     * @return 分组类型数组
     */
    Class<?>[] groups() default {};

    /**
     * Bean Validation 载荷。
     *
     * @return 载荷类型数组
     */
    Class<? extends Payload>[] payload() default {};
}
