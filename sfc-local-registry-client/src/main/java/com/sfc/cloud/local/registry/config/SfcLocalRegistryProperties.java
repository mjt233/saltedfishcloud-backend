package com.sfc.cloud.local.registry.config;

import lombok.Data;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * local 注册中心客户端配置，前缀 {@code spring.cloud.sfc-local}。
 */
@Data
@ConfigurationProperties("spring.cloud.sfc-local")
public class SfcLocalRegistryProperties {
    /** 是否启用客户端；默认 true。未配置 server-addr 时不会产生任何 Bean */
    private boolean enabled = true;

    /** 注册中心地址，支持 {@code host:port} 或 {@code http(s)://host:port} */
    private String serverAddr;

    /** 注册密码，必须与服务端 {@code sys.service.local-registry.password} 一致 */
    @ToString.Exclude
    private String password;

    /** 服务名，留空时取 {@code spring.application.name} */
    private String serviceName;

    /** 实例 ID，留空时使用 {@code host:port} */
    private String instanceId;

    /** 注册到注册中心的 host，留空时取本机地址 */
    private String host;

    /** 是否通过 HTTPS 访问注册中心 */
    private boolean secure;

    /** 注册中心上下文路径，留空表示根路径 */
    private String contextPath;

    /** 实例元数据 */
    private Map<String, String> metadata = new LinkedHashMap<>();

    /** 心跳间隔 */
    private Duration heartbeatInterval = Duration.ofSeconds(10);

    /** 连接超时 */
    private Duration connectTimeout = Duration.ofSeconds(3);

    /** 读超时 */
    private Duration readTimeout = Duration.ofSeconds(5);
}
