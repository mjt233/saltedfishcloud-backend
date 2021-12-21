package com.xiaotao.saltedfishcloud.annotations;


import com.xiaotao.saltedfishcloud.enums.ReadOnlyLevel;

import java.lang.annotation.*;

/**
 * 当系统处于只读状态时阻止控制器的执行<br>
 * 注解位于类级下时，该控制器类下的所有方法都会被拒绝执行，除非方法被注解 @NotBlock 所标记
 * @see NotBlock
 */
@Documented
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ReadOnlyBlock {
    /**
     * 只读级别，当系统的只读级别与改属性匹配时将阻塞方法的执行
     */
    ReadOnlyLevel[] level() default {ReadOnlyLevel.DATA_CHECKING, ReadOnlyLevel.DATA_MOVING};
}
