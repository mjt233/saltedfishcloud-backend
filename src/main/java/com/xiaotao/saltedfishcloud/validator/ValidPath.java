package com.xiaotao.saltedfishcloud.validator;

import javax.validation.Constraint;
import javax.validation.Payload;
import java.lang.annotation.*;



/**
 * 验证字段是否是一个合法的文件路径
 * 包含/../或以/..结尾或以../开头的字符串会认作不合法
 */
@Documented
@Target({ElementType.PARAMETER, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ValidPathValidator.class)
public @interface ValidPath {
    String message() default "非法的路径，不得包含/../或使用/..结尾";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
