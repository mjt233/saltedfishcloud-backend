package com.xiaotao.saltedfishcloud.annotations;

import java.lang.annotation.*;

/**
 * 当返回结果为null时，返回为0
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface NullToZero {
}
