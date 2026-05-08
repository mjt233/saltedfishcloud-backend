package com.sfc.task.config;

import com.sfc.task.AsyncTaskExecutor;
import com.sfc.task.AsyncTaskReceiver;
import com.sfc.task.DefaultAsyncTaskExecutor;
import com.sfc.task.receiver.DefaultTaskReceiver;
import com.sfc.task.repo.AsyncTaskRecordRepo;
import com.xiaotao.saltedfishcloud.service.mq.MQService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@Slf4j
@ComponentScan("com.sfc.task")
@EntityScan("com.sfc.task.model")
@EnableJpaRepositories(basePackages = "com.sfc.task.repo")
public class AsyncTaskAutoConfiguration implements InitializingBean {

    @Override
    public void afterPropertiesSet() throws Exception {
        log.info("初始化任务模块配置类");
    }

    @Bean
    public AsyncTaskReceiver asyncTaskReceiver(AsyncTaskRecordRepo asyncTaskRecordRepo, MQService mqService) {
        return new DefaultTaskReceiver(asyncTaskRecordRepo, mqService);
    }

    @Bean
    public AsyncTaskExecutor asyncTaskExecutor(AsyncTaskRecordRepo asyncTaskRecordRepo, MQService mqService) {
        return new DefaultAsyncTaskExecutor(asyncTaskReceiver(asyncTaskRecordRepo, mqService));
    }
}
