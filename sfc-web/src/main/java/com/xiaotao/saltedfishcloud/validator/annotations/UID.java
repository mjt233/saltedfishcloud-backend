package com.xiaotao.saltedfishcloud.validator.annotations;


import com.xiaotao.saltedfishcloud.validator.UIDValidator;

import javax.validation.Constraint;
import javax.validation.Payload;
import java.lang.annotation.*;

/**
 * 验证是否具有操作UID的权限
 */
@Documented
@Target({ElementType.PARAMETER, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = UIDValidator.class)
public @interface UID {
    /**
     * 是否仅允许管理员操作
     */
    boolean value() default false;
    String message() default "无权操作资源";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
