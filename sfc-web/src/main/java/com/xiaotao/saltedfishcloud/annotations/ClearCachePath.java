package com.xiaotao.saltedfishcloud.annotations;

import java.lang.annotation.*;

@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ClearCachePath {
    String[] value();
}
