package com.xiaotao.saltedfishcloud.config.cache;

import com.xiaotao.saltedfishcloud.cache.CacheService;
import com.xiaotao.saltedfishcloud.cache.RedisCacheServiceImpl;
import com.xiaotao.saltedfishcloud.constant.CacheNames;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;
import java.util.Objects;

/**
 * Redis 缓存配置类。
 * 当 sys.service.cache-provider=redis（或未配置）时启用。
 */
@Configuration
@ConditionalOnProperty(prefix = "sys.service", name = "cache-provider", havingValue = "redis", matchIfMissing = true)
public class RedisCacheConfig {

    /**
     * 当 sys.service.cache-provider=redis（或未配置）时，启用Redis缓存。
     *
     * @param redisTemplate RedisTemplate Bean
     * @return Redis缓存管理器
     */
    @Bean
    public CacheManager cacheManager(RedisTemplate<String, Object> redisTemplate) {
        RedisCacheConfiguration redisCacheConfiguration = getRedisCacheConfig(redisTemplate);
        RedisConnectionFactory connectionFactory = Objects.requireNonNull(redisTemplate.getConnectionFactory());
        return RedisCacheManager.RedisCacheManagerBuilder.fromConnectionFactory(connectionFactory)
                .cacheDefaults(redisCacheConfiguration)
                .withCacheConfiguration(CacheNames.PATH, redisCacheConfiguration.entryTtl(Duration.ofHours(12)))
                .withCacheConfiguration(CacheNames.DEFAULT, redisCacheConfiguration.entryTtl(Duration.ofHours(6)))
                .withCacheConfiguration(CacheNames.PROXY_TEST_RESULT, redisCacheConfiguration.entryTtl(Duration.ofMinutes(1)))
                .build();
    }

    /**
     * 当 sys.service.cache-provider=redis（或未配置）时，注入Redis实现的缓存服务。
     *
     * @param redisTemplate RedisTemplate Bean
     * @return Redis缓存服务实现
     */
    @Bean
    public CacheService cacheService(RedisTemplate<String, Object> redisTemplate) {
        return new RedisCacheServiceImpl(redisTemplate);
    }

    /**
     * 获取一个Redis缓存配置
     *
     * @param redisTemplate RedisTemplate Bean
     * @return  Redis缓存配置
     */
    private RedisCacheConfiguration getRedisCacheConfig(RedisTemplate<String, Object> redisTemplate) {
        return RedisCacheConfiguration.defaultCacheConfig()
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(redisTemplate.getValueSerializer())
                )
                .entryTtl(Duration.ofMinutes(30));
    }
}
