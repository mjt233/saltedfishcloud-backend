package com.xiaotao.saltedfishcloud.config;

import com.xiaotao.saltedfishcloud.cache.CacheService;
import com.xiaotao.saltedfishcloud.cache.LocalCacheServiceImpl;
import com.xiaotao.saltedfishcloud.cache.RedisCacheServiceImpl;
import com.xiaotao.saltedfishcloud.constant.CacheNames;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
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

    /**
     * 本地缓存服务固定缓存名。
     */
    private static final String LOCAL_CACHE_SERVICE = "cache-service-local";
    /**
     * 当 sys.service.cache-provider=redis（或未配置）时，启用Redis缓存。
     *
     * @param redisTemplate RedisTemplate Bean
     * @return Redis缓存管理器
     */
    @Bean
    @ConditionalOnProperty(prefix = "sys.service", name = "cache-provider", havingValue = "redis", matchIfMissing = true)
    public CacheManager redisCacheManager(RedisTemplate<String, Object> redisTemplate) {
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
     * 当 sys.service.cache-provider=local 时，启用进程内缓存。
     *
     * @return 本地内存缓存管理器
     */
    @Bean
    @ConditionalOnProperty(prefix = "sys.service", name = "cache-provider", havingValue = "local")
    public CacheManager localCacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager(
                CacheNames.PATH,
                CacheNames.DEFAULT,
                CacheNames.PROXY_TEST_RESULT,
                LOCAL_CACHE_SERVICE
        );
        cacheManager.setCacheSpecification("maximumSize=20000,expireAfterWrite=30m");
        return cacheManager;
    }

    /**
     * 当 sys.service.cache-provider=redis（或未配置）时，注入Redis实现的缓存服务。
     *
     * @param redisTemplate RedisTemplate Bean
     * @return Redis缓存服务实现
     */
    @Bean
    @ConditionalOnProperty(prefix = "sys.service", name = "cache-provider", havingValue = "redis", matchIfMissing = true)
    public CacheService redisCacheService(RedisTemplate<String, Object> redisTemplate) {
        return new RedisCacheServiceImpl(redisTemplate);
    }

    /**
     * 当 sys.service.cache-provider=local 时，注入本地实现的缓存服务。
     *
     * @return 本地缓存服务实现
     */
    @Bean
    @ConditionalOnProperty(prefix = "sys.service", name = "cache-provider", havingValue = "local")
    public CacheService localCacheService(CacheManager cacheManager) {
        return new LocalCacheServiceImpl(cacheManager);
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
