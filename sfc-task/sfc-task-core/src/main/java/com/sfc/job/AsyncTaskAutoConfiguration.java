package com.sfc.job;

import com.xiaotao.saltedfishcloud.utils.MapperHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.Objects;

@Configuration
public class AsyncTaskAutoConfiguration {

    @Autowired
    private RedisConnectionFactory factory;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private RedisMessageListenerContainer redisMessageListenerContainer;

    @Bean
    public AsyncTaskManager newAsyncTaskManager() {
        return new AsyncTaskManagerImpl(redisTemplate, redisMessageListenerContainer);
    }

    @Bean
    public RPCManager rpcManager() {
        return new RPCManager(redisTemplate, redisMessageListenerContainer);
    }


    @Bean
    @ConditionalOnMissingBean(name = "redisTemplate")
    public RedisTemplate redisTemplate() {
        RedisTemplate template = new RedisTemplate<>();
        Jackson2JsonRedisSerializer serializer = new Jackson2JsonRedisSerializer(Object.class);
        serializer.setObjectMapper(MapperHolder.mapper);

        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(serializer);
        template.setConnectionFactory(factory);
        return template;
    }

    @Bean
    @ConditionalOnMissingBean(name = "cacheManager")
    public RedisCacheManager cacheManager() {
        return RedisCacheManager.RedisCacheManagerBuilder.fromConnectionFactory(
                        Objects.requireNonNull(redisTemplate().getConnectionFactory()))
                .cacheDefaults(getCacheConfig())
                .withCacheConfiguration("path", getCacheConfig().entryTtl(Duration.ofHours(12)))
                .withCacheConfiguration("default", getCacheConfig().entryTtl(Duration.ofHours(6)))
                .build();
    }

    @Bean
    @ConditionalOnMissingBean(name = "redisMessageListenerContainer")
    public RedisMessageListenerContainer redisMessageListenerContainer(RedisConnectionFactory factory) {
        final RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(factory);
        return container;
    }



    /**
     * 获取一个Redis缓存配置
     * @return  Redis缓存配置
     */
    private RedisCacheConfiguration getCacheConfig() {
        return RedisCacheConfiguration.defaultCacheConfig()
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(redisTemplate().getValueSerializer())
                );
    }
}
