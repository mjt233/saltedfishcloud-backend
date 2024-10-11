package com.xiaotao.saltedfishcloud.enums;

/**
 * 消息队列订阅偏移策略
 */
public enum MQOffsetStrategy {
    /**
     * 从头开始消费
     */
    AT_HEAD,
    /**
     * 从尾部开始消费
     */
    AT_TAIL,
    /**
     * 自定义起始消费点
     */
    AT_CUSTOM
}
