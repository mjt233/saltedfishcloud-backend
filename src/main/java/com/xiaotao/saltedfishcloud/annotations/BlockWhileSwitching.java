package com.xiaotao.saltedfishcloud.annotations;


import java.lang.annotation.*;

/**
 * 当系统处于存储切换状态时阻止执行<br>
 * 注解位于类级下时，该控制器类下的所有方法都会被切换中状态所阻塞，除非方法被注解 @NotBlock 所标记
 * @see NotBlock
 */
@Documented
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface BlockWhileSwitching {
}
