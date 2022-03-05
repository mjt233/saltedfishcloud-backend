package com.xiaotao.saltedfishcloud.orm.config.annotation;



import java.lang.annotation.*;

/**
 * 标志字段属性为配置节点
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.TYPE})
public @interface ConfigKey {
}
