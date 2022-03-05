package com.xiaotao.saltedfishcloud.orm.config.annotation;

import com.xiaotao.saltedfishcloud.orm.config.enums.EntityKeyType;

import java.lang.annotation.*;

/**
 * 配置类实体
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ConfigEntity {
    /**
     * 配置节点名称
     */
    String value();

    /**
     * 实体定义类型
     */
    EntityKeyType keyType() default EntityKeyType.ALL;
}
