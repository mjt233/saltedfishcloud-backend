package com.xiaotao.saltedfishcloud.service.mq;

/**
 * 消息队列消息主题
 * @param <T>   该类主题的消息体类型
 */
public abstract class MQTopic<T> {
    protected final MQTopicProvider<T> provider;
    protected MQTopic(MQTopicProvider<T> provider) {
        this.provider = provider;
    }
    public String getTopic() {
        return this.provider.getTopic();
    }
}
