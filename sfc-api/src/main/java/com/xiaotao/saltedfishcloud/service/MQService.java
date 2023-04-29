package com.xiaotao.saltedfishcloud.service;

import com.xiaotao.saltedfishcloud.model.MQMessage;

import java.util.function.Consumer;

/**
 * 消息队列服务
 */
public interface MQService {

    /**
     * 发送消息广播
     * @param topic 主题
     * @param msg   消息内容
     */
    void sendBroadcast(String topic, Object msg);

    /**
     * 订阅消息广播
     * @param topic 订阅主题
     * @param consumer  消费函数
     * @return  订阅者id
     */
    long subscribeBroadcast(String topic, Consumer<MQMessage> consumer);

    /**
     * 取消订阅
     * @param id    订阅者id
     */
    void unsubscribe(Long id);
}
