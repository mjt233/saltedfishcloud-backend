package com.xiaotao.saltedfishcloud.config;

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
public class RedisConfig {
    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    public RedisTemplate<String, Object> getRedisTemplate() {
        return redisTemplate;
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(LettuceConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.setConnectionFactory(connectionFactory);
        return template;
    }

    @Bean
    public RedisCacheManager cacheManager() {
        return RedisCacheManager.RedisCacheManagerBuilder.fromConnectionFactory(Objects.requireNonNull(redisTemplate.getConnectionFactory()))
                .cacheDefaults(getRedisCacheConfigurationWithTtl(Duration.ofHours(3)))
                .withCacheConfiguration("token", getRedisCacheConfigurationWithTtl(Duration.ofDays(1)))
                .build();
    }

    private RedisCacheConfiguration getRedisCacheConfigurationWithTtl(Duration time) {
        return RedisCacheConfiguration.defaultCacheConfig()
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(redisTemplate.getValueSerializer())
                ).disableCachingNullValues()
                .entryTtl(time);
    }
}
