package com.xiaotao.saltedfishcloud.annotations;

import com.xiaotao.saltedfishcloud.enums.ProtectLevel;

import java.lang.annotation.*;

/**
 * 被该注解标记的方法不会被方法所在类的 @ProtectBlock 影响 <br>
 * 即相当于类级阻塞注解的例外方法<br>
 * @see ProtectBlock
 */
@Documented
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface NotBlock {
    /**
     * 忽略保护阻塞的级别
     */
    ProtectLevel[] level() default {ProtectLevel.DATA_CHECKING, ProtectLevel.DATA_MOVING};
}
