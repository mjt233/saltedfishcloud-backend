package com.xiaotao.saltedfishcloud.annotations;

import java.lang.annotation.*;

/**
 * 配置参数实体类
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ConfigPropertyEntity {
    /**
     * 配置前缀，会统一添加到字段的属性中，并自动以.结尾
     */
    String prefix() default "";

    ConfigPropertiesGroup[] groups() default { @ConfigPropertiesGroup(id = "base", name = "配置信息")};
}
