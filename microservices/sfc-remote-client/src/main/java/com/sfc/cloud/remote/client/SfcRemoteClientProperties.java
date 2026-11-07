package com.sfc.cloud.remote.client;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 远程客户端配置项，前缀 {@code sfc.remote.client}。
 *
 * <p>目标地址不支持直连配置：始终按服务名经 {@code LoadBalancerClient} 选取实例。</p>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "sfc.remote.client")
public class SfcRemoteClientProperties {

    /** 连接超时；为 null 时使用 JDK HttpClient 默认值 */
    private Duration connectTimeout = Duration.ofSeconds(5);

    /**
     * 读取超时；{@link Duration#ZERO} 表示不限制（大文件上传建议放宽或设为 0）。
     */
    private Duration readTimeout = Duration.ofMinutes(30);
}
