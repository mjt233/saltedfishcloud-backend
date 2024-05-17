package com.xiaotao.saltedfishcloud.config;

import com.sfc.constant.CacheNames;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;
import java.util.Objects;

@Configuration
public class CacheConfig {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Bean
    public RedisCacheManager cacheManager() {
        RedisConnectionFactory connectionFactory = Objects.requireNonNull(redisTemplate.getConnectionFactory());
        return RedisCacheManager.RedisCacheManagerBuilder.fromConnectionFactory(connectionFactory)
                .cacheDefaults(getCacheConfig())
                .withCacheConfiguration(CacheNames.PATH, getCacheConfig().entryTtl(Duration.ofHours(12)))
                .withCacheConfiguration(CacheNames.DEFAULT, getCacheConfig().entryTtl(Duration.ofHours(6)))
                .withCacheConfiguration(CacheNames.PROXY_TEST_RESULT, getCacheConfig().entryTtl(Duration.ofMinutes(1)))
                .build();
    }

    /**
     * 获取一个Redis缓存配置
     * @return  Redis缓存配置
     */
    private RedisCacheConfiguration getCacheConfig() {
        return RedisCacheConfiguration.defaultCacheConfig()
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(redisTemplate.getValueSerializer())
                )
                .entryTtl(Duration.ofMinutes(30));
    }
}
