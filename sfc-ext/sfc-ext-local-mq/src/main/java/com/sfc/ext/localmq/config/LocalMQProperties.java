package com.sfc.ext.localmq.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 本地消息队列配置属性。
 */
@Data
@ConfigurationProperties(prefix = "sys.service.local-mq")
public class LocalMQProperties {
    /**
     * 队列最大消息数。0 或负数表示不限制。默认 4096。
     */
    private int maxQueueSize = 4096;
    /**
     * 淘汰目标比例。队列消息数超过 {@link #maxQueueSize} 时，淘汰到 {@code maxQueueSize * evictTargetRatio}。
     * 取值范围 (0, 1]，默认 0.85。
     */
    private double evictTargetRatio = 0.85;
}
