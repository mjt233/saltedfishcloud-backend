package com.xiaotao.saltedfishcloud.service.breakpoint.annotation;

import java.lang.annotation.*;

/**
 * 标记控制器方法支持使用断点续传
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface BreakPoint {

}
