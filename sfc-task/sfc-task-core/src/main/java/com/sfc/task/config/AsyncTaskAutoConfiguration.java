package com.sfc.task.config;

import com.sfc.task.*;
import com.sfc.task.model.AsyncTaskRecord;
import com.sfc.task.rpc.RPCManager;
import com.xiaotao.saltedfishcloud.utils.MapperHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.util.concurrent.TimeUnit;

@Configuration
@Slf4j
@ComponentScan("com.sfc.task")
public class AsyncTaskAutoConfiguration implements InitializingBean {
    @Override
    public void afterPropertiesSet() throws Exception {
        log.info("初始化任务模块配置类");
    }

    @Autowired
    private RedisConnectionFactory redisConnectionFactory;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Bean
    public AsyncTaskManager newAsyncTaskManager() {
        return new AsyncTaskManagerImpl(asyncTaskExecutor());
    }

    @Bean
    public RPCManager rpcManager() {
        return new RPCManager(redisConnectionFactory);
    }

    @Bean
    public TestAsyncTaskFactory testAsyncTaskFactory() {
        return new TestAsyncTaskFactory();
    }

    @Bean
    @ConditionalOnMissingBean(RedisTemplate.class)
    public RedisTemplate<String, Object> redisTemplate() {
        RedisTemplate template = new RedisTemplate<>();
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer(MapperHolder.mapper));
        template.setConnectionFactory(redisConnectionFactory);
        return template;
    }

    @Bean
    public AsyncTaskExecutor asyncTaskExecutor() {
        return new DefaultAsyncTaskExecutor(() -> {
            Object o = null;
            while (o == null) {
                try {
                    log.debug("开始接受任务");
                    o = redisTemplate.opsForList().rightPop(AsyncTaskConstants.RedisKey.TASK_QUEUE, 120, TimeUnit.SECONDS);
                    log.debug("收到任务: {}", o);
                    if (o == null) {
                        Thread.sleep(100);
                        continue;
                    }
                    if (o instanceof String) {
                        return MapperHolder.parseJson((String) o, AsyncTaskRecord.class);
                    } else if (o instanceof AsyncTaskRecord) {
                        return (AsyncTaskRecord) o;
                    } else {
                        throw new IllegalArgumentException("任务反序列化失败");
                    }
                } catch (Throwable e) {
                    throw new RuntimeException("任务接受出错:" + e.getMessage(), e);
                }
            }
            return null;
        });
    }
}
