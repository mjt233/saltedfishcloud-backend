package com.xiaotao.saltedfishcloud.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.sfc.constant.CacheNames;
import com.xiaotao.saltedfishcloud.utils.MapperHolder;
import jodd.util.ThreadUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.Record;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Executors;

@Configuration
@Slf4j
public class RedisConfig {
    private final RedisConnectionFactory factory;

    @Autowired
    public RedisConfig(RedisConnectionFactory factory) {
        this.factory = factory;
    }

    @Bean
    public StreamMessageListenerContainer<String, MapRecord<String, String, String>> stringRecordStreamMessageListenerContainer() {;
        StreamMessageListenerContainer<String, MapRecord<String, String, String>> container = StreamMessageListenerContainer.create(factory, StreamMessageListenerContainer.StreamMessageListenerContainerOptions.builder()
                .batchSize(10)
                .errorHandler(t -> log.error("[消息队列]监听出现错误: ", t))
                .pollTimeout(Duration.ofSeconds(10))
                .executor(Executors.newCachedThreadPool())
                .build());
        container.start();
        return container;
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate() {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        StringRedisSerializer stringRedisSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringRedisSerializer);
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer(createValueSerializerMapper()));
        template.setHashKeySerializer(stringRedisSerializer);
        template.setHashValueSerializer(stringRedisSerializer);
        template.setConnectionFactory(factory);
        return template;
    }

    private ObjectMapper createValueSerializerMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.activateDefaultTyping(mapper.getPolymorphicTypeValidator(), ObjectMapper.DefaultTyping.NON_FINAL, JsonTypeInfo.As.PROPERTY);
        mapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        mapper.enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.setTypeFactory(mapper.getTypeFactory().withClassLoader(Thread.currentThread().getContextClassLoader()));
        return mapper;
    }

    @Bean
    public RedisCacheManager cacheManager() {
        return RedisCacheManager.RedisCacheManagerBuilder.fromConnectionFactory(
                Objects.requireNonNull(redisTemplate().getConnectionFactory()))
                .cacheDefaults(getCacheConfig())
                .withCacheConfiguration(CacheNames.PATH, getCacheConfig().entryTtl(Duration.ofHours(12)))
                .withCacheConfiguration(CacheNames.DEFAULT, getCacheConfig().entryTtl(Duration.ofHours(6)))
                .withCacheConfiguration(CacheNames.PROXY_TEST_RESULT, getCacheConfig().entryTtl(Duration.ofMinutes(1)))
                .build();
    }

    @Bean
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
