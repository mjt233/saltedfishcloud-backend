package com.sfc.job;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration
public class AsyncTaskAutoConfiguration {
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private RedisMessageListenerContainer redisMessageListenerContainer;

    public AsyncTaskManager newAsyncTaskManager() {
        return new AsyncTaskManagerImpl(redisTemplate, redisMessageListenerContainer);
    }

    public RPCManager rpcManager() {
        return new RPCManager(redisTemplate, redisMessageListenerContainer);
    }
}
