package com.xiaotao.saltedfishcloud.annotations;

import com.xiaotao.saltedfishcloud.utils.identifier.SnowFlakeIdGenerator;
import org.hibernate.annotations.IdGeneratorType;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * 雪花id
 */
@IdGeneratorType(SnowFlakeIdGenerator.class)
@Retention(RetentionPolicy.RUNTIME)
public @interface SnowFlakeId {
}
