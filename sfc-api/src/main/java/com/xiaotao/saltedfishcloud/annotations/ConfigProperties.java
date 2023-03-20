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
     * 配置项名称，若ConfigPropertiesEntity注解中配置了prefix，则会默认添加prefix前缀。<br>
     * 若未设定该值，则默认使用字段名的短横线命名作为配置项名。如：isEnable会自动转为is-enable。若存在prefix如quickshare，则组合后为：quickshare.is-enable
     */
    String value() default "";

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
     * 是否必填
     */
    boolean required() default false;

    /**
     * 显示的标题名称
     */
    String title() default "";

    /**
     * 所在配置组
     */
    String group() default "base";

    /**
     * 是否掩盖显示为“*”
     */
    boolean isMask() default false;
}
