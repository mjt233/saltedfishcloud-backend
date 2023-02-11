package com.sfc.task.annocation;

import com.sfc.task.config.AsyncTaskAutoConfiguration;
import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

/**
 * 启用异步任务系统
 */
@Retention(RetentionPolicy.RUNTIME)
@Import(AsyncTaskAutoConfiguration.class)
@Target(ElementType.TYPE)
@Documented
public @interface EnableAsyncTask {
}
