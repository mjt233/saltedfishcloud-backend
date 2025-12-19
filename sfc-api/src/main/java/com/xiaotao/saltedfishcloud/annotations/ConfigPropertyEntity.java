package com.xiaotao.saltedfishcloud.annotations;

import com.xiaotao.saltedfishcloud.utils.PropertyUtils;

import java.lang.annotation.*;

/**
 * 配置参数实体类。存在该注解的类可通过{@link PropertyUtils#getConfigNodeFromEntityClass(java.lang.Class)}来解析成配置参数
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ConfigPropertyEntity {
    /**
     * 配置前缀，会统一添加到字段的属性中，并自动以.结尾
     */
    String prefix() default "";

    /**
     * 实体类字段的默认配置key命名生成策略
     */
    ConfigKeyNameStrategy defaultKeyNameStrategy() default ConfigKeyNameStrategy.KEBAB_CASE;

    ConfigPropertiesGroup[] groups() default { @ConfigPropertiesGroup(id = "base", name = "配置信息")};
}
