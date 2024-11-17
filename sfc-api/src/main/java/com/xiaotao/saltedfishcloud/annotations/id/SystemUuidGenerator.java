package com.xiaotao.saltedfishcloud.annotations.id;

import org.hibernate.annotations.IdGeneratorType;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * 不带横杠“-”的系统UUID生成器
 */
@IdGeneratorType(com.xiaotao.saltedfishcloud.utils.identifier.SystemUuidGenerator.class)
@Retention(RetentionPolicy.RUNTIME)
public @interface SystemUuidGenerator {
}
