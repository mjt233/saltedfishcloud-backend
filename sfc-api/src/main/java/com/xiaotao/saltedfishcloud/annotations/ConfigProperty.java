package com.xiaotao.saltedfishcloud.annotations;

import com.xiaotao.saltedfishcloud.constant.ConfigInputType;

import java.lang.annotation.*;

/**
 * 标识该类为组合的参数类，以json形式作为单个配置项的值
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface ConfigProperty {

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
     * 输入类型，可选text、select、checkbox、radio
     */
    String inputType() default "text";

    /**
     * 当配置项的inputType为"select"时的候选值
     */
    ConfigSelectOption[] options() default {};

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

    /**
     * 是否独占一行显示
     */
    boolean isRow() default false;

    /**
     * 当{@link #inputType()}为{@link ConfigInputType#TEMPLATE}时，使用的模板组件
     */
    String template() default "";

    /**
     * 当{@link #inputType()}为{@link ConfigInputType#TEMPLATE}时，给模板传入的参数json
     */
    String templateParams() default "{}";

}
