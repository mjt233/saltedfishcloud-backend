package com.xiaotao.saltedfishcloud.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 消息队列消息
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class MQMessage implements Serializable {
    /**
     * 主题
     */
    private String topic;

    /**
     * 消息体
     */
    private Object body;
}
