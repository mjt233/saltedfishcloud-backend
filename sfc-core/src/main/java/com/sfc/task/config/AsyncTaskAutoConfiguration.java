package com.sfc.task.config;

import com.sfc.task.AsyncTaskExecutor;
import com.sfc.task.DefaultAsyncTaskExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * 异步任务模块自动配置。
 * <p>任务调度相关的组件（worker 注册中心、认领服务、健康检查、上报服务、中断路由等）由
 * 组件扫描自动装配；本配置类只负责声明需要手工构造的本地任务执行器，以及注册实体与仓库扫描路径。</p>
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

    /**
     * 本地任务执行器。只负责执行被认领到的任务，任务认领由本地 worker 按任务类型完成。
     *
     * @return 本地任务执行器
     */
    @Bean
    public AsyncTaskExecutor asyncTaskExecutor() {
        return new DefaultAsyncTaskExecutor();
    }
}
