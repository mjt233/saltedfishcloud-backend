package com.xiaotao.saltedfishcloud.annotations;

import com.xiaotao.saltedfishcloud.enums.ReadOnlyLevel;

import java.lang.annotation.*;

/**
 * 被该注解标记的方法不会被方法所在类的 @ReadOnlyBlock 影响 <br>
 * 即相当于类级阻塞注解的例外方法<br>
 * @see ReadOnlyBlock
 */
@Documented
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface NotBlock {
    /**
     * 忽略只读阻塞的级别
     */
    ReadOnlyLevel[] level() default {ReadOnlyLevel.DATA_CHECKING, ReadOnlyLevel.DATA_MOVING};
}
