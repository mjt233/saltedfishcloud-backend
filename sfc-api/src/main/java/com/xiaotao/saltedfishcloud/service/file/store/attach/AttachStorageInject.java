package com.xiaotao.saltedfishcloud.service.file.store.attach;

import java.lang.annotation.*;

/**
 * 标记需要自动注入 {@link AttachStorage} 实例的字段。
 * <p>
 * 被该注解标记的字段会在 Bean 初始化时自动完成存储域注册和存储实例注入。
 * <pre>{@code
 * @AttachStorageInject(value = "thumbnail", name = "缩略图缓存")
 * private AttachStorage thumbnailStorage;
 * }</pre>
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AttachStorageInject {

    /**
     * 存储域唯一标识
     */
    String value();

    /**
     * 存储域显示名称，未指定时默认使用 {@link #value()}
     */
    String name() default "";

    /**
     * 存储域描述
     */
    String description() default "";
}
