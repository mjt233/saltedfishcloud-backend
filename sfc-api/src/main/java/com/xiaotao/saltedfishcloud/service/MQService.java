package com.xiaotao.saltedfishcloud.service;

import com.xiaotao.saltedfishcloud.enums.MQOffsetStrategy;
import com.xiaotao.saltedfishcloud.model.MQMessage;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
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
     * 创建消息队列
     * @param topic 队列主题
     */
    void createQueue(String topic);

    /**
     * 销毁消息队列
     * @param topic 队列主题
     */
    void destroyQueue(String topic);

    /**
     * 订阅消息队列，不消费历史数据
     * @param topic     队列主题
     * @param group     消费组
     * @param consumer  消费函数
     * @return          订阅者id
     */
    long subscribeMessageQueue(String topic, String group, Consumer<MQMessage> consumer);

    /**
     * 订阅消息队列
     * @param topic     队列主题
     * @param group     消费组
     * @param offsetStrategy    消费偏移策略
     * @param offsetPoint       指定的偏移点（消息记录id），当offsetStrategy为{@link  MQOffsetStrategy#AT_CUSTOM}时生效，表示从该点开始消费。
     * @param consumer          消费函数
     * @return                  订阅者id
     */
    long subscribeMessageQueue(String topic, String group, MQOffsetStrategy offsetStrategy, @Nullable String offsetPoint, Consumer<MQMessage> consumer);

    /**
     * 取消消息队列订阅
     * @param id    订阅者id
     */
    void unsubscribeMessageQueue(Long id);

    /**
     * 推送消息到队列
     * @param topic     消息队列主题
     * @param message   消息内容
     */
    void push(String topic, Object message) throws IOException;

    /**
     * 取消订阅
     * @param id    订阅者id
     */
    void unsubscribe(Long id);
}
