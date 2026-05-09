package com.sfc.ext.localmq.core;

import com.xiaotao.saltedfishcloud.model.MQMessage;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * 本地广播订阅注册中心。
 */
@Slf4j
final class LocalMQBroadcastRegistry {
    /**
     * 日志前缀。
     */
    private static final String LOG_PREFIX = "[本地消息队列]";

    /**
     * 广播主题订阅者映射。
     */
    private final ConcurrentHashMap<String, ConcurrentHashMap<Long, Consumer<MQMessage>>> broadcastSubscribers = new ConcurrentHashMap<>();

    /**
     * 发送广播消息。
     *
     * @param topic 主题
     * @param message 消息内容
     */
    void sendBroadcast(String topic, Object message) {
        MQMessage mqMessage = MQMessage.builder().topic(topic).body(message).build();
        ConcurrentHashMap<Long, Consumer<MQMessage>> subscribers = broadcastSubscribers.get(topic);
        if (subscribers == null) {
            return;
        }
        subscribers.values().forEach(consumer -> safeConsumeBroadcast(topic, consumer, mqMessage));
    }

    /**
     * 注册广播订阅者。
     *
     * @param topic 主题
     * @param subscriberId 订阅者 ID
     * @param consumer 消费函数
     */
    void subscribe(String topic, long subscriberId, Consumer<MQMessage> consumer) {
        broadcastSubscribers.computeIfAbsent(topic, key -> new ConcurrentHashMap<>()).put(subscriberId, consumer);
    }

    /**
     * 移除广播订阅者。
     *
     * @param id 订阅者 ID
     * @return 是否移除了订阅
     */
    boolean unsubscribe(Long id) {
        AtomicBoolean removed = new AtomicBoolean(false);
        broadcastSubscribers.values().forEach(subscribers -> {
            if (subscribers.remove(id) != null) {
                removed.set(true);
            }
        });
        return removed.get();
    }

    /**
     * 清空全部广播订阅。
     */
    void clear() {
        broadcastSubscribers.clear();
    }

    /**
     * 安全执行广播消费函数。
     *
     * @param topic 主题
     * @param consumer 消费函数
     * @param message 广播消息
     */
    private void safeConsumeBroadcast(String topic, Consumer<MQMessage> consumer, MQMessage message) {
        try {
            consumer.accept(message);
        } catch (Throwable throwable) {
            log.error("{}广播主题 {} 消费失败", LOG_PREFIX, topic, throwable);
        }
    }
}
