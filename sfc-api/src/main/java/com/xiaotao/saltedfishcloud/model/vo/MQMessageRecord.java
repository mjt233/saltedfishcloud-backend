package com.xiaotao.saltedfishcloud.model.vo;

/**
 * 消息队列消息
 *
 * @param <T>
 */
public record MQMessageRecord<T>(String topic, T body) {
}
