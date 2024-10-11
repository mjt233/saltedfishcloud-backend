package com.xiaotao.saltedfishcloud.annotations;

import java.lang.annotation.*;

// todo 支持自定义url表达式
@Documented
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface AllowAnonymous {
}
