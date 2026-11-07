package com.xiaotao.saltedfishcloud.service.registry.local;

import lombok.Data;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * local 注册中心服务端配置，前缀 {@code spring.cloud.sfc-local.registry}。
 * <p>
 * core 作为 local 注册中心服务端：除登记本节点自身实例外，还通过 {@code /api/local-registry/**}
 * 接受外部服务注册。配置节点风格与 {@code spring.cloud.nacos.discovery.*} 对齐；
 * 是否启用由 {@code spring.cloud.sfc-local.registry.enabled}（默认 {@code true}）控制，
 * 与 Nacos 注册中心应二选一。
 */
@Data
@ConfigurationProperties(prefix = SfcLocalRegistryServerProperties.PREFIX)
public class SfcLocalRegistryServerProperties {
    /** 配置前缀 */
    public static final String PREFIX = "spring.cloud.sfc-local.registry";

    /**
     * 是否启用 core 的进程内 local 注册中心；默认 {@code true}。
     * <p>与 {@code spring.cloud.nacos.discovery.enabled} 应二选一：同时启用会因存在两个
     * {@code Registration} Bean 导致应用启动失败。</p>
     */
    private boolean enabled = true;

    /**
     * 外部服务注册密码。留空表示禁用外部注册接口（fail-closed）。
     */
    @ToString.Exclude
    private String password;

    /**
     * 实例心跳超时时间，超过该时间未收到心跳的实例会被移除。
     */
    private Duration heartbeatTimeout = Duration.ofSeconds(30);

    /**
     * 过期实例清理任务执行间隔。
     */
    private Duration cleanupInterval = Duration.ofSeconds(10);
}
