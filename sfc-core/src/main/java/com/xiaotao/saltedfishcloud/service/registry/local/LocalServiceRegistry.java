package com.xiaotao.saltedfishcloud.service.registry.local;

import com.sfc.common.exception.SfcJsonException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.client.serviceregistry.ServiceRegistry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 进程内服务注册与发现实现（{@code sys.service.registry-provider=local}，默认值）。
 * <p>
 * 未启用微服务模式时，注册关系只在当前进程内维护，因此本实现不提供 TTL、续约、死实例清理与主动健康检查。
 * 对外的契约完全使用 Spring Cloud 官方接口：写入侧实现 {@link ServiceRegistry}，读取侧实现 {@link DiscoveryClient}。
 * <p>
 * 注册表按 {@code serviceId -> (instanceId -> 实例快照)} 组织。注册时写入 {@link DefaultServiceInstance} 快照，
 * 避免把仍在回填端口的 {@link LocalRegistration} 暴露给发现侧。
 */
@Slf4j
public class LocalServiceRegistry implements DiscoveryClient, ServiceRegistry<LocalRegistration> {
    /** 注册表：serviceId -> (instanceId -> 实例快照) */
    private final Map<String, Map<String, ServiceInstance>> registry = new ConcurrentHashMap<>();

    /**
     * 登记实例。重复调用按 instanceId 幂等覆盖。
     *
     * @param registration 当前实例注册信息
     */
    @Override
    public void register(LocalRegistration registration) {
        if (registration == null || !hasText(registration.getServiceId())) {
            throw new SfcJsonException("服务注册失败：serviceId 不能为空");
        }
        if (!hasText(registration.getHost()) || registration.getPort() <= 0) {
            throw new SfcJsonException("服务注册失败：host 不能为空且 port 必须大于 0");
        }
        DefaultServiceInstance snapshot = new DefaultServiceInstance(
                instanceIdOf(registration),
                registration.getServiceId(),
                registration.getHost(),
                registration.getPort(),
                registration.isSecure(),
                registration.getMetadata());
        registry.computeIfAbsent(registration.getServiceId(), key -> new ConcurrentHashMap<>())
                .put(snapshot.getInstanceId(), snapshot);
        log.info("[服务注册] 进程内注册表已登记 serviceId={} instanceId={}",
                snapshot.getServiceId(), snapshot.getInstanceId());
    }

    /**
     * 注销实例，只移除匹配 serviceId 与 instanceId 的那一条。
     *
     * @param registration 当前实例注册信息
     */
    @Override
    public void deregister(LocalRegistration registration) {
        if (registration == null || !hasText(registration.getServiceId())) {
            return;
        }
        Map<String, ServiceInstance> instances = registry.get(registration.getServiceId());
        if (instances == null) {
            return;
        }
        String instanceId = instanceIdOf(registration);
        instances.remove(instanceId);
        if (instances.isEmpty()) {
            registry.remove(registration.getServiceId(), instances);
        }
        log.info("[服务注销] 进程内注册表已移除 serviceId={} instanceId={}", registration.getServiceId(), instanceId);
    }

    /**
     * 清空进程内注册表。
     */
    @Override
    public void close() {
        int size = registry.size();
        registry.clear();
        log.info("[服务注册] 进程内注册表已清空，原有服务数量：{}", size);
    }

    /**
     * 更新实例状态：{@code UP} 重新登记，{@code DOWN} 注销。
     *
     * @param registration 当前实例注册信息
     * @param status       状态值
     */
    @Override
    public void setStatus(LocalRegistration registration, String status) {
        if ("UP".equalsIgnoreCase(status)) {
            register(registration);
            return;
        }
        if ("DOWN".equalsIgnoreCase(status)) {
            deregister(registration);
            return;
        }
        throw new IllegalArgumentException("不支持的实例状态：" + status);
    }

    /**
     * 查询实例状态。
     *
     * @param registration 当前实例注册信息
     * @param <T>          状态类型
     * @return {@code UP} 或 {@code DOWN}
     */
    @Override
    @SuppressWarnings("unchecked")
    public <T> T getStatus(LocalRegistration registration) {
        boolean present = registration != null
                && registry.getOrDefault(registration.getServiceId(), Collections.emptyMap())
                        .containsKey(instanceIdOf(registration));
        return (T) (present ? "UP" : "DOWN");
    }

    /**
     * 客户端描述。
     *
     * @return 描述文本
     */
    @Override
    public String description() {
        return "Local in-memory discovery client";
    }

    /**
     * 按服务名列出实例。
     *
     * @param serviceId 服务名
     * @return 实例列表，无匹配时返回空列表
     */
    @Override
    public List<ServiceInstance> getInstances(String serviceId) {
        if (!hasText(serviceId)) {
            return Collections.emptyList();
        }
        Map<String, ServiceInstance> instances = registry.get(serviceId.trim());
        return instances == null ? Collections.emptyList() : new ArrayList<>(instances.values());
    }

    /**
     * 列出已知服务名。
     *
     * @return 服务名列表
     */
    @Override
    public List<String> getServices() {
        return new ArrayList<>(registry.keySet());
    }

    /**
     * 取实例 ID：优先使用实例自带的 instanceId，为空时退化为 {@code host:port}。
     *
     * @param instance 实例
     * @return 实例 ID
     */
    private static String instanceIdOf(ServiceInstance instance) {
        String instanceId = instance.getInstanceId();
        return hasText(instanceId) ? instanceId : instance.getHost() + ":" + instance.getPort();
    }

    /**
     * 判断字符串非 null 且含非空白字符。
     *
     * @param value 待判断字符串
     * @return 非空白时返回 true
     */
    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
