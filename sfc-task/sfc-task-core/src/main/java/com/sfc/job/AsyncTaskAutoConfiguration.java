package com.sfc.job;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;

@Configuration
public class AsyncTaskAutoConfiguration {

    @Autowired
    private RedisConnectionFactory redisConnectionFactory;

    @Bean
    public AsyncTaskManager newAsyncTaskManager() {
        return new AsyncTaskManagerImpl();
    }

    @Bean
    public RPCManager rpcManager() {
        return new RPCManager(redisConnectionFactory);
    }
}
