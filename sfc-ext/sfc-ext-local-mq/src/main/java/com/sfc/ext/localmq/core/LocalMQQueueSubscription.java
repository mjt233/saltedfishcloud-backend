package com.sfc.ext.localmq.core;

import com.xiaotao.saltedfishcloud.model.MQMessage;
import lombok.Getter;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * 队列订阅元数据。
 */
@Getter
final class LocalMQQueueSubscription {
    /**
     * 订阅 ID。
     */
    private final long id;

    /**
     * 队列主题。
     */
    private final String topic;

    /**
     * 消费组。
     */
    private final String group;

    /**
     * 关联的队列状态。
     */
    private final LocalMQQueueState queueState;

    /**
     * 消费函数。
     */
    private final Consumer<MQMessage> consumer;

    /**
     * 订阅活动状态。
     */
    private final AtomicBoolean active = new AtomicBoolean(true);

    /**
     * 创建队列订阅元数据。
     *
     * @param id 订阅 ID
     * @param topic 队列主题
     * @param group 消费组
     * @param queueState 队列状态
     * @param consumer 消费函数
     */
    LocalMQQueueSubscription(long id, String topic, String group, LocalMQQueueState queueState, Consumer<MQMessage> consumer) {
        this.id = id;
        this.topic = topic;
        this.group = group;
        this.queueState = queueState;
        this.consumer = consumer;
    }

    /**
     * 判断订阅是否仍处于活动状态。
     *
     * @return 订阅活动状态
     */
    boolean isActive() {
        return active.get();
    }

    /**
     * 停止当前订阅。
     */
    void stop() {
        active.set(false);
    }
}
