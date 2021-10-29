package com.xiaotao.saltedfishcloud.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import javax.annotation.Resource;
import java.time.Duration;
import java.util.Objects;

@Configuration
@RequiredArgsConstructor
public class RedisConfig {
    private final LettuceConnectionFactory factory;

    @Bean
    public RedisTemplate<String, Object> redisTemplate() {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.setConnectionFactory(factory);
        return template;
    }

    @Bean
    public RedisCacheManager cacheManager(LettuceConnectionFactory factory) {
        return RedisCacheManager.RedisCacheManagerBuilder.fromConnectionFactory(
                Objects.requireNonNull(redisTemplate().getConnectionFactory()))
                .cacheDefaults(getCacheConfigWitTtl(Duration.ofHours(3)))
                .build();
    }

    /**
     * 获取一个带过期时间的Redis缓存配置
     * @param time  过期时间
     * @return  Redis缓存配置
     */
    private RedisCacheConfiguration getCacheConfigWitTtl(Duration time) {
        return RedisCacheConfiguration.defaultCacheConfig()
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(redisTemplate().getValueSerializer())
                ).disableCachingNullValues()
                .entryTtl(time);
    }
}
