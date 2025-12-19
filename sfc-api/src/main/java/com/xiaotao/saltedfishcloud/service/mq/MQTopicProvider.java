package com.xiaotao.saltedfishcloud.service.mq;

@FunctionalInterface
public interface MQTopicProvider<T> {
    String getTopic();
}
