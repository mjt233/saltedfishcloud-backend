package com.xiaotao.saltedfishcloud.config;

import com.xiaotao.saltedfishcloud.service.RedisMQService;
import com.xiaotao.saltedfishcloud.service.mq.MQService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;

/**
 * {@link MQService} Provider 自动配置。
 * <p>
 * 提供核心模块内置的 Redis {@link MQService} 自动配置。
 * <p>
 * 本地 MQ 实现已迁移到 {@code sfc-ext-local-mq} 插件，由插件负责在
 * {@code sys.service.mq-provider=local} 时提供对应 Bean。
 */
@Slf4j
@Configuration
public class MQAutoConfiguration {

    /**
     * 注册 Redis MQ 实现。
     *
     * @param redisTemplate RedisTemplate
     * @param redisMessageListenerContainer Redis 消息监听容器
     * @param stringStreamMessageListenerContainer Redis Stream 监听容器
     * @return Redis MQ 服务实现
     */
    @Bean
    @ConditionalOnProperty(prefix = "sys.service", name = "mq-provider", havingValue = "redis", matchIfMissing = true)
    public MQService redisMqService(
            RedisTemplate<String, Object> redisTemplate,
            RedisMessageListenerContainer redisMessageListenerContainer,
            StreamMessageListenerContainer<String, MapRecord<String, String, String>> stringStreamMessageListenerContainer
    ) {
        log.info("MQService provider: redis");
        return new RedisMQService(redisTemplate, redisMessageListenerContainer, stringStreamMessageListenerContainer);
    }
}
