package com.xiaotao.saltedfishcloud.service.async;

import com.xiaotao.saltedfishcloud.service.async.context.TaskContextFactory;
import com.xiaotao.saltedfishcloud.service.async.context.TaskManager;
import com.xiaotao.saltedfishcloud.service.async.context.TaskManagerImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AsyncTaskAutoConfigurator {
    @Bean
    public TaskManager asyncTaskManager() {
        return new TaskManagerImpl();
    }

    @Bean
    public TaskContextFactory factory() {
        return new TaskContextFactory(asyncTaskManager());
    }
}
