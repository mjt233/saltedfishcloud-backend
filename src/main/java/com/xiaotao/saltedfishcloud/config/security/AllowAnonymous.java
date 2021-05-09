package com.xiaotao.saltedfishcloud.config.security;

import java.lang.annotation.*;

@Documented
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface AllowAnonymous {
}
