package com.sfc.ext.localmq.core;

import lombok.Getter;

/**
 * 本地队列消息记录。
 */
@Getter
final class LocalMQQueueMessageRecord {
    /**
     * 消息记录 ID。
     */
    private final long id;

    /**
     * 队列主题。
     */
    private final String topic;

    /**
     * 消息内容。
     */
    private final Object body;

    /**
     * 创建本地队列消息记录。
     *
     * @param id 消息记录 ID
     * @param topic 队列主题
     * @param body 消息内容
     */
    LocalMQQueueMessageRecord(long id, String topic, Object body) {
        this.id = id;
        this.topic = topic;
        this.body = body;
    }
}
