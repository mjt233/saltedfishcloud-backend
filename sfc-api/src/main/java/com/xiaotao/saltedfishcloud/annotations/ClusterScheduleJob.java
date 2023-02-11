package com.xiaotao.saltedfishcloud.annotations;

import java.lang.annotation.*;

/**
 * 定义这个方法是集群异步任务方法
 */
@Target(ElementType.METHOD)
@Documented
@Retention(RetentionPolicy.RUNTIME)
public @interface ClusterScheduleJob {
    /**
     * 任务名称
     */
    String value();

    /**
     * 任务描述
     */
    String describe() default "";
}
