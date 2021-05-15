package com.xiaotao.saltedfishcloud.annotations;


import java.lang.annotation.*;

/**
 * 当系统处于存储切换状态时阻止执行
 */
@Documented
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface BlockWhileSwitching {
}
