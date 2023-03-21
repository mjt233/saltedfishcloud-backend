package com.xiaotao.saltedfishcloud.service;

import java.util.function.Consumer;

/**
 * 消息队列服务
 */
public interface MQService {

    /**
     * 发送消息
     * @param topic 主题
     * @param msg   消息内容
     */
    void send(String topic, Object msg);

    /**
     * 订阅
     * @param topic 订阅主题
     * @param consumer  消费函数
     * @return  订阅者id
     */
    long subscribe(String topic, Consumer<Object> consumer);

    /**
     * 取消订阅
     * @param id    订阅者id
     */
    void unsubscribe(Long id);
}
