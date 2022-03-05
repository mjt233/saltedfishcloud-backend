package com.xiaotao.saltedfishcloud.orm.config.annotation;

import java.lang.annotation.*;

/**
 * 忽略的配置字段
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface IgnoreConfigKey {
}
