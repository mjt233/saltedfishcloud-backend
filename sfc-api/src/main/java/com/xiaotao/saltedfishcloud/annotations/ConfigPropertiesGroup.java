package com.xiaotao.saltedfishcloud.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Documented
@Retention(RetentionPolicy.RUNTIME)
public @interface ConfigPropertiesGroup {
    /**
     * 组id
     */
    String id();

    /**
     * 配置组名称
     */
    String name();

    /**
     * 描述
     */
    String describe() default "";
}
