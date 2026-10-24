package com.sfc.task.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * 异步任务模块自动配置
 */
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
}
