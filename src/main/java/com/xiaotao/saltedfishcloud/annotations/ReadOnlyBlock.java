package com.xiaotao.saltedfishcloud.annotations;


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
}
