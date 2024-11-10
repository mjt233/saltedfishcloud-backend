package com.xiaotao.saltedfishcloud.validator.annotations;

import com.xiaotao.saltedfishcloud.validator.FileNameValidator;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

@Documented
@Target({ElementType.PARAMETER, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = FileNameValidator.class)
public @interface FileName {
    String message() default "非法文件名，不可包含/\\<>?|:换行符，回车符或文件名为..";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
