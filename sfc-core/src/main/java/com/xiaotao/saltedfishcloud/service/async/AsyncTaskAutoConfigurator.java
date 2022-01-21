package com.xiaotao.saltedfishcloud.service.async;

import com.xiaotao.saltedfishcloud.service.async.context.TaskContextFactory;
import com.xiaotao.saltedfishcloud.service.async.context.TaskContextFactoryImpl;
import com.xiaotao.saltedfishcloud.service.async.context.TaskManager;
import com.xiaotao.saltedfishcloud.service.async.context.TaskManagerImpl;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AsyncTaskAutoConfigurator {
    @Bean
    @ConditionalOnMissingBean(TaskManager.class)
    public TaskManager asyncTaskManager() {
        return new TaskManagerImpl();
    }

    @Bean
    @ConditionalOnMissingBean(TaskContextFactory.class)
    public TaskContextFactory factory() {
        return new TaskContextFactoryImpl(asyncTaskManager());
    }
}
