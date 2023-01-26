package com.sfc.job;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class AsyncTaskManagerImpl implements AsyncTaskManager {
    private final RedisTemplate<String, Object> redisTemplate;
    private final RedisMessageListenerContainer redisMessageListenerContainer;


    private final Map<String, AsyncTaskFactory> factoryMap = new HashMap<>();

    public AsyncTaskManagerImpl(RedisTemplate<String, Object> redisTemplate,
                                RedisMessageListenerContainer redisMessageListenerContainer) {
        this.redisTemplate = redisTemplate;
        this.redisMessageListenerContainer = redisMessageListenerContainer;
    }

    @Override
    public void registerFactory(String type, AsyncTaskFactory factory) {
        if (factoryMap.containsKey(type)) {
            throw new IllegalArgumentException("任务类型 " + type + " 的任务工厂已被注册");
        }
        factoryMap.put(type, factory);
    }

    @Override
    public void submitAsyncTask(String type, AsyncTaskRecord record) {
        AsyncTaskFactory factory = factoryMap.get(type);
        if (factory == null) {
            throw new IllegalArgumentException("找不到类型 " + type + "的任务工厂");
        }
    }

    @Override
    public InputStream getTaskLog(Long taskId, boolean withHistory) {

        return null;
    }
}
