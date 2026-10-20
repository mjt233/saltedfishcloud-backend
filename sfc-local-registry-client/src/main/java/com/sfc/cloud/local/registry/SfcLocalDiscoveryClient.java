package com.sfc.cloud.local.registry;

import com.sfc.cloud.local.registry.model.SfcLocalServiceInstance;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * local 注册中心的客户端发现实现。
 * <p>
 * 查询失败时返回空列表并限频记录日志，避免注册中心短暂不可用时拖垮调用方。
 */
@Slf4j
public class SfcLocalDiscoveryClient implements DiscoveryClient {
    /** 日志前缀 */
    private static final String LOG_PREFIX = "[SFC local registry]";

    /** HTTP 客户端 */
    private final SfcLocalRegistryHttpClient httpClient;

    /** 最近一次错误消息，用于避免重复刷日志 */
    private final AtomicReference<String> lastError = new AtomicReference<>();

    /**
     * 创建客户端发现实现。
     *
     * @param httpClient HTTP 客户端
     */
    public SfcLocalDiscoveryClient(SfcLocalRegistryHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    /**
     * 按服务名查询实例。
     *
     * @param serviceId 服务名
     * @return 实例列表，查询失败时为空列表
     */
    @Override
    public List<ServiceInstance> getInstances(String serviceId) {
        if (serviceId == null || serviceId.isBlank()) {
            return List.of();
        }
        try {
            List<SfcLocalServiceInstance> instances = httpClient.getInstances(serviceId.trim());
            lastError.set(null);
            return instances.stream()
                    .map(instance -> new DefaultServiceInstance(
                            instance.getInstanceId(),
                            instance.getServiceId(),
                            instance.getHost(),
                            instance.getPort(),
                            instance.isSecure(),
                            instance.getMetadata()))
                    .collect(Collectors.toList());
        } catch (RuntimeException e) {
            logFailure(e);
            return List.of();
        }
    }

    /**
     * 查询所有服务名。
     *
     * @return 服务名列表，查询失败时为空列表
     */
    @Override
    public List<String> getServices() {
        try {
            List<String> services = httpClient.getServices();
            lastError.set(null);
            return services;
        } catch (RuntimeException e) {
            logFailure(e);
            return List.of();
        }
    }

    /**
     * 客户端描述。
     *
     * @return 描述文本
     */
    @Override
    public String description() {
        return "SFC local registry discovery client";
    }

    /**
     * 仅在错误消息变化时输出一次错误日志。
     *
     * @param error 异常
     */
    private void logFailure(RuntimeException error) {
        String message = error.getMessage();
        if (!Objects.equals(lastError.getAndSet(message), message)) {
            log.warn("{}查询注册中心失败：{}", LOG_PREFIX, message);
        }
    }
}
