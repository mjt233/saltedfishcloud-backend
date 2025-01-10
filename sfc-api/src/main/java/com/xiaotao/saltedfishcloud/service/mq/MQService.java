package com.xiaotao.saltedfishcloud.service.mq;

import com.xiaotao.saltedfishcloud.enums.MQOffsetStrategy;
import com.xiaotao.saltedfishcloud.model.MQMessage;
import com.xiaotao.saltedfishcloud.model.vo.MQMessageRecord;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.Serializable;
import java.util.function.Consumer;

/**
 * 消息队列服务
 * todo 使用注解方式订阅广播/队列消息
 */
public interface MQService {

    /**
     * 发送消息广播。对于结构化的消息推荐使用 {@link #sendBroadcast(MQTopic, Object)}
     * @param topic 主题
     * @param msg   消息内容
     */
    void sendBroadcast(String topic, Object msg);


    /**
     * 发送消息广播
     * @param topic 主题
     * @param msg   消息内容
     */
    <T> void sendBroadcast(MQTopic<T> topic, T msg);

    /**
     * 订阅消息广播
     * @param topic 订阅主题
     * @param consumer  消费函数，
     * @return  订阅者id
     */
    <T> long subscribeBroadcast(MQTopic<T> topic, Consumer<MQMessageRecord<T>> consumer);

    /**
     * 订阅消息广播。对于结构化的消息推荐使用 {@link #subscribeBroadcast(MQTopic, Consumer)}
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
     * 创建消息队列
     * @param topic 队列主题
     */
    <T> void createQueue(MQTopic<T> topic);

    /**
     * 销毁消息队列
     * @param topic 队列主题
     */
    void destroyQueue(String topic);

    /**
     * 销毁消息队列
     * @param topic 队列主题
     */
    void destroyQueue(MQTopic<?> topic);

    /**
     * 订阅消息队列，不消费历史数据。对于复杂的结构化消息对象，推荐使用{@link #subscribeMessageQueue(MQTopic, String, Consumer)}
     * @param topic     队列主题
     * @param group     消费组
     * @param consumer  消费函数
     * @return          订阅者id
     */
    long subscribeMessageQueue(String topic, String group, Consumer<MQMessage> consumer);


    /**
     * 订阅消息队列，不消费历史数据
     * @param topic     队列主题
     * @param group     消费组
     * @param consumer  消费函数
     * @return          订阅者id
     */
    <T> long subscribeMessageQueue(MQTopic<T> topic, String group, Consumer<MQMessageRecord<T>> consumer);

    /**
     * 订阅消息队列。对于复杂的结构化消息对象，推荐使用{@link #subscribeMessageQueue(MQTopic, String, MQOffsetStrategy, String, Consumer)}
     * @param topic     队列主题
     * @param group     消费组
     * @param offsetStrategy    消费偏移策略
     * @param offsetPoint       指定的偏移点（消息记录id），当offsetStrategy为{@link  MQOffsetStrategy#AT_CUSTOM}时生效，表示从该点开始消费。
     * @param consumer          消费函数
     * @return                  订阅者id
     */
    long subscribeMessageQueue(String topic, String group, MQOffsetStrategy offsetStrategy, @Nullable String offsetPoint, Consumer<MQMessage> consumer);

    /**
     * 订阅消息队列
     * @param topic     队列主题
     * @param group     消费组
     * @param offsetStrategy    消费偏移策略
     * @param offsetPoint       指定的偏移点（消息记录id），当offsetStrategy为{@link  MQOffsetStrategy#AT_CUSTOM}时生效，表示从该点开始消费。
     * @param consumer          消费函数
     * @return                  订阅者id
     */
    <T> long subscribeMessageQueue(MQTopic<T> topic, String group, MQOffsetStrategy offsetStrategy, @Nullable String offsetPoint, Consumer<MQMessageRecord<T>> consumer);

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
     * 推送消息到队列
     * @param topic     消息队列主题
     * @param message   消息内容
     */
    <T> void push(MQTopic<T> topic, T message) throws IOException;

    /**
     * 取消订阅
     * @param id    订阅者id
     */
    void unsubscribe(Long id);
}
