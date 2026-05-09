package com.sfc.ext.localmq.core;

import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 本地队列状态。
 */
@Getter
final class LocalMQQueueState {
    /**
     * 队列主题。
     */
    private final String topic;

    /**
     * 队列消息列表。
     */
    private final List<LocalMQQueueMessageRecord> messages = new ArrayList<>();

    /**
     * 消费组偏移量。
     */
    private final ConcurrentHashMap<String, Integer> groupOffsets = new ConcurrentHashMap<>();

    /**
     * 消费组订阅数量。
     */
    private final ConcurrentHashMap<String, Integer> groupSubscriptionCounts = new ConcurrentHashMap<>();

    /**
     * 队列监视器。
     */
    private final Object monitor = new Object();

    /**
     * 创建本地队列状态。
     *
     * @param topic 队列主题
     */
    LocalMQQueueState(String topic) {
        this.topic = topic;
    }
}
