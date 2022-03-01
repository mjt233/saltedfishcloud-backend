package com.xiaotao.saltedfishcloud.orm.config.annotation;

import com.xiaotao.saltedfishcloud.orm.config.enums.EntityType;

import java.lang.annotation.*;

/**
 * 配置类实体
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ConfigEntity {
    String value();
    EntityType type() default EntityType.PROPERTIES;
}
