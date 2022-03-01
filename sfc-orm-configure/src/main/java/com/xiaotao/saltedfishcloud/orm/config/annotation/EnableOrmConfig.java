package com.xiaotao.saltedfishcloud.orm.config.annotation;

import com.xiaotao.saltedfishcloud.orm.config.OrmConfigAutoConfigure;
import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Import(OrmConfigAutoConfigure.class)
public @interface EnableOrmConfig {
}
