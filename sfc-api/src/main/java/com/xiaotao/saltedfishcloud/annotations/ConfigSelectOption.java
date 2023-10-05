package com.xiaotao.saltedfishcloud.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * 当配置项的inputType为"select"时的候选值
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
public @interface ConfigSelectOption {
    String title();

    String value();
}
