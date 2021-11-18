package com.xiaotao.saltedfishcloud.validator.annotations;

import com.xiaotao.saltedfishcloud.validator.UsernameValidator;

import javax.validation.Constraint;
import javax.validation.Payload;
import java.lang.annotation.*;

@Documented
@Target({ElementType.PARAMETER, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = UsernameValidator.class)
public @interface Username {
    String message() default "不合法的用户名，不可包含:|*?!^&$`#@%;[]{}<>\\/";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
