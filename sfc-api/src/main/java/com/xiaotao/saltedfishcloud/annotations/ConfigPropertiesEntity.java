package com.xiaotao.saltedfishcloud.annotations;

import java.lang.annotation.*;
import java.util.Collections;

/**
 * 配置参数实体类
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ConfigPropertiesEntity {
    ConfigPropertiesGroup[] groups() default { @ConfigPropertiesGroup(id = "base", name = "配置信息")};
}
