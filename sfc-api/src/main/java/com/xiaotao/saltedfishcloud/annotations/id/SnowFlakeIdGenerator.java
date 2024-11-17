package com.xiaotao.saltedfishcloud.annotations.id;

import org.hibernate.annotations.IdGeneratorType;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * 雪花id
 */
@IdGeneratorType(com.xiaotao.saltedfishcloud.utils.identifier.SnowFlakeIdGenerator.class)
@Retention(RetentionPolicy.RUNTIME)
public @interface SnowFlakeIdGenerator {
}
