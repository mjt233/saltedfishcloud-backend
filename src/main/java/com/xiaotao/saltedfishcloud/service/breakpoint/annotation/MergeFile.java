package com.xiaotao.saltedfishcloud.service.breakpoint.annotation;

import java.lang.annotation.*;

/**
 * 标记控制器方法的MultiFile合并分块任务文件
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface MergeFile {
}
