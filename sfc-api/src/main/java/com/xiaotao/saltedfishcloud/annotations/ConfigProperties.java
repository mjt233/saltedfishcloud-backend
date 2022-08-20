package com.xiaotao.saltedfishcloud.annotations;

import java.lang.annotation.*;

/**
 * 标识该类为组合的参数类，以json形式作为单个配置项的值
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface ConfigProperties {

    /**
     * 配置标题
     */
    String value();

    /**
     * 默认值
     */
    String defaultValue() default "";

    /**
     * 配置描述
     */
    String describe() default "";

    /**
     * 输入类型，可选text、select、checkbox
     */
    String inputType() default "text";

    /**
     * 所在配置组
     */
    String group() default "base";
}
