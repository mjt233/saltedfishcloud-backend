package com.sfc.cloud.data.id;

import org.hibernate.annotations.IdGeneratorType;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * 雪花id主键生成策略注解。
 *
 * <p>标注在 JPA 实体的主键字段上，Hibernate 将使用
 * {@link SfcSnowFlakeIdGeneratorImpl} 为该字段生成主键值。</p>
 *
 * <p><b>命名说明</b>：宿主侧存在同名但不同包的一对类型
 * （注解 {@code annotations.id.SnowFlakeIdGenerator} 与实现
 * {@code utils.identifier.SnowFlakeIdGenerator}）。本模块刻意用不同简单名区分二者——
 * 注解为 {@code SfcSnowFlakeIdGenerator}，实现类为 {@link SfcSnowFlakeIdGeneratorImpl}，
 * 避免同一包下出现同名类型。</p>
 *
 * @see SfcSnowFlakeIdGeneratorImpl
 * @see SfcIdUtil#getId()
 */
@IdGeneratorType(com.sfc.cloud.data.id.SfcSnowFlakeIdGeneratorImpl.class)
@Retention(RetentionPolicy.RUNTIME)
public @interface SfcSnowFlakeIdGenerator {
}
