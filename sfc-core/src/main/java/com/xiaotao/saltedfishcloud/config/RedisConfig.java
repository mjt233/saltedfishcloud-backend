package com.xiaotao.saltedfishcloud.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;

import java.time.Duration;
import java.util.concurrent.Executors;

/**
 * Redis 配置类，负责配置 RedisTemplate、消息监听容器等。
 * 当 sys.redis.enabled=true（默认值）时启用。
 */
@Configuration
@ConditionalOnProperty(prefix = "sys.redis", name = "enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class RedisConfig {
    private final RedisConnectionFactory factory;

    /**
     * 构造 Redis 配置。
     *
     * @param factory Redis 连接工厂
     */
    @Autowired
    public RedisConfig(RedisConnectionFactory factory) {
        this.factory = factory;
    }

    /**
     * 创建 Redis Stream 消息监听容器。
     * 用于处理 Redis Stream 中的消息（如异步任务的消息队列）。
     *
     * @return Redis Stream 消息监听���器，用于接收和处理 Redis Stream 消息
     */
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

    /**
     * 创建并配置 RedisTemplate Bean。
     * 用于与 Redis 交互，提供一致的序列化/反序列化策略。
     *
     * @return RedisTemplate 实例，支持对象序列化
     */
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

    /**
     * 创建 Redis 发布/订阅消息监听容器。
     * 用于监听 Redis 发布/订阅消息（如 RPC 调用、事件通知等）。
     *
     * @param factory Redis 连接工厂
     * @return Redis 消息监听容器
     */
    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(RedisConnectionFactory factory) {
        final RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(factory);
        return container;
    }
}
