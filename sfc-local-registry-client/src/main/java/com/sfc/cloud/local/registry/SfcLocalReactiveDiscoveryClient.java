package com.sfc.cloud.local.registry;

import com.sfc.cloud.local.registry.model.SfcLocalServiceInstance;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.ReactiveDiscoveryClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * local 注册中心的反应式客户端发现实现
 */
@NullMarked
@Slf4j
public class SfcLocalReactiveDiscoveryClient implements ReactiveDiscoveryClient {
    /** 日志前缀 */
    private static final String LOG_PREFIX = "[SFC local registry]";

    /** HTTP 客户端 */
    private final SfcLocalRegistryHttpClient httpClient;

    /** 最近一次错误消息，用于避免重复刷日志 */
    private final AtomicReference<@Nullable String> lastError = new AtomicReference<>();

    /**
     * 创建反应式客户端发现实现。
     *
     * @param httpClient HTTP 客户端
     */
    public SfcLocalReactiveDiscoveryClient(SfcLocalRegistryHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    /**
     * 按服务名异步查询实例。
     *
     * @param serviceId 服务名
     * @return 实例序列，查询失败时为空序列
     */
    @Override
    public Flux<ServiceInstance> getInstances(String serviceId) {
        if (serviceId.isBlank()) {
            return Flux.empty();
        }
        return Mono.fromFuture(() -> httpClient.getInstancesAsync(serviceId.trim()))
                .doOnNext(ignored -> lastError.set(null))
                .flatMapMany(Flux::fromIterable)
                .map(SfcLocalReactiveDiscoveryClient::toServiceInstance)
                .onErrorResume(error -> {
                    logFailure(error);
                    return Flux.empty();
                });
    }

    /**
     * 异步查询所有服务名。
     *
     * @return 服务名序列，查询失败时为空序列
     */
    @Override
    public Flux<String> getServices() {
        return Mono.fromFuture(httpClient::getServicesAsync)
                .doOnNext(ignored -> lastError.set(null))
                .flatMapMany(Flux::fromIterable)
                .onErrorResume(error -> {
                    logFailure(error);
                    return Flux.empty();
                });
    }

    /**
     * 客户端描述。
     *
     * @return 描述文本
     */
    @Override
    public String description() {
        return "SFC local registry reactive discovery client";
    }

    /**
     * 把注册中心 DTO 转换为 Spring Cloud 官方实例描述。
     *
     * @param instance 注册中心返回的实例
     * @return Spring Cloud 实例
     */
    private static ServiceInstance toServiceInstance(SfcLocalServiceInstance instance) {
        return new DefaultServiceInstance(
                instance.getInstanceId(),
                instance.getServiceId(),
                instance.getHost(),
                instance.getPort(),
                instance.isSecure(),
                instance.getMetadata());
    }

    /**
     * 仅在错误消息变化时输出一次错误日志。
     *
     * @param error 异常
     */
    private void logFailure(Throwable error) {
        String message = error.getMessage();
        if (!Objects.equals(lastError.getAndSet(message), message)) {
            log.warn("{}反应式查询注册中心失败：{}", LOG_PREFIX, message);
        }
    }
}
