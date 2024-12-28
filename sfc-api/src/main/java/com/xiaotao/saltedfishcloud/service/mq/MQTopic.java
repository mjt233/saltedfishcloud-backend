package com.xiaotao.saltedfishcloud.service.mq;

public abstract class MQTopic<T> {
    protected final MQTopicProvider<T> provider;
    protected MQTopic(MQTopicProvider<T> provider) {
        this.provider = provider;
    }
    public String getTopic() {
        return this.provider.getTopic();
    }
}
