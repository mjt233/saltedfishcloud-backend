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
     * 队列最大消息数。0 或负数表示不限制。默认 2000。
     */
    private int maxQueueSize = 2000;
}
