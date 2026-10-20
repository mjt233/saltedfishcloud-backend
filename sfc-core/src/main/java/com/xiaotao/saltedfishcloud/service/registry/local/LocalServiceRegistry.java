package com.xiaotao.saltedfishcloud.service.registry.local;

import com.sfc.cloud.local.registry.model.SfcLocalRegisterRequest;
import com.sfc.cloud.local.registry.model.SfcLocalRegisterResult;
import com.sfc.common.exception.SfcJsonException;
import com.xiaotao.saltedfishcloud.config.SysProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.client.serviceregistry.ServiceRegistry;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 进程内服务注册与发现实现（{@code sys.service.registry-provider=local}，默认值）。
 * <p>
 * 除宿主自身实例外，还接受外部服务通过 {@code /api/local-registry/**} 注册。外部实例
 * 通过心跳续约，超过 {@code sys.service.local-registry.heartbeat-timeout} 未续约时被移除。
 * 对外的契约完全使用 Spring Cloud 官方接口：写入侧实现 {@link ServiceRegistry}，
 * 读取侧实现 {@link DiscoveryClient}。
 * <p>
 * 注册表按 {@code serviceId -> (instanceId -> 实例快照)} 组织。注册时写入 {@link DefaultServiceInstance} 快照，
 * 避免把仍在回填端口的 {@link LocalRegistration} 暴露给发现侧。
 */
@Slf4j
public class LocalServiceRegistry implements DiscoveryClient, ServiceRegistry<LocalRegistration> {
    /** 服务名最大长度 */
    private static final int MAX_SERVICE_ID_LENGTH = 128;

    /** 实例 ID 最大长度 */
    private static final int MAX_INSTANCE_ID_LENGTH = 256;

    /** host 最大长度 */
    private static final int MAX_HOST_LENGTH = 255;

    /** 元数据最大条目数 */
    private static final int MAX_METADATA_ENTRIES = 64;

    /** 元数据 key 最大长度 */
    private static final int MAX_METADATA_KEY_LENGTH = 128;

    /** 元数据 value 最大长度 */
    private static final int MAX_METADATA_VALUE_LENGTH = 1024;

    /** 默认心跳超时 */
    private static final Duration DEFAULT_HEARTBEAT_TIMEOUT = Duration.ofSeconds(30);

    /** 注册表：serviceId -> (instanceId -> 实例快照) */
    private final Map<String, Map<String, ServiceInstance>> registry = new ConcurrentHashMap<>();

    /** 外部实例表：serviceId -> (instanceId -> 带过期时间的实例快照) */
    private final Map<String, Map<String, ExternalInstance>> externalRegistry = new ConcurrentHashMap<>();

    /** 系统配置，用于读取本地注册中心的心跳超时 */
    private final SysProperties sysProperties;

    /** 注册表是否已关闭 */
    private volatile boolean closed;

    /**
     * 创建进程内注册表。
     *
     * @param sysProperties 系统配置
     */
    public LocalServiceRegistry(SysProperties sysProperties) {
        this.sysProperties = sysProperties;
    }

    /**
     * 登记宿主自身实例。重复调用按 instanceId 幂等覆盖。
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
     * 注销宿主自身实例，只移除匹配 serviceId 与 instanceId 的那一条。
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
     * 登记外部服务实例。
     * <p>
     * 同一 serviceId + instanceId 重复注册时覆盖旧实例并重置过期时间。
     *
     * @param request 外部实例注册请求
     * @return 注册结果（服务端确认的实例 ID 与心跳超时秒数）
     */
    public SfcLocalRegisterResult registerExternal(SfcLocalRegisterRequest request) {
        if (request == null) {
            throw new SfcJsonException(400, "注册请求不能为空");
        }
        String serviceId = trimmed(request.getServiceId());
        String host = trimmed(request.getHost());
        validateText(serviceId, "serviceId", MAX_SERVICE_ID_LENGTH);
        validateText(host, "host", MAX_HOST_LENGTH);
        if (request.getPort() <= 0 || request.getPort() > 65535) {
            throw new SfcJsonException(400, "port 必须在 1-65535 之间");
        }

        String instanceId = trimmed(request.getInstanceId());
        if (!hasText(instanceId)) {
            instanceId = host + ":" + request.getPort();
        }
        if (instanceId.length() > MAX_INSTANCE_ID_LENGTH) {
            throw new SfcJsonException(400, "instanceId 长度不能超过 " + MAX_INSTANCE_ID_LENGTH);
        }

        Map<String, String> metadata = validateMetadata(request.getMetadata());
        DefaultServiceInstance snapshot = new DefaultServiceInstance(
                instanceId,
                serviceId,
                host,
                request.getPort(),
                request.isSecure(),
                metadata);
        long timeoutNanos = heartbeatTimeoutNanos();
        externalRegistry.computeIfAbsent(serviceId, key -> new ConcurrentHashMap<>())
                .put(instanceId, new ExternalInstance(snapshot, System.nanoTime() + timeoutNanos));
        log.info("[服务注册] 外部实例已登记 serviceId={} instanceId={}", serviceId, instanceId);
        return new SfcLocalRegisterResult(instanceId,
                Math.max(1, TimeUnit.NANOSECONDS.toSeconds(timeoutNanos)));
    }

    /**
     * 外部实例心跳续约。
     *
     * @param serviceId  服务名
     * @param instanceId 实例 ID
     * @return 续约后的心跳超时秒数
     */
    public long heartbeatExternal(String serviceId, String instanceId) {
        String normalizedServiceId = trimmed(serviceId);
        String normalizedInstanceId = trimmed(instanceId);
        if (!hasText(normalizedServiceId) || !hasText(normalizedInstanceId)) {
            throw new SfcJsonException(400, "serviceId 与 instanceId 不能为空");
        }
        long timeoutNanos = heartbeatTimeoutNanos();
        Map<String, ExternalInstance> instances = externalRegistry.get(normalizedServiceId);
        ExternalInstance externalInstance = instances == null
                ? null
                : instances.computeIfPresent(normalizedInstanceId, (key, value) -> {
                    if (isExpired(value, System.nanoTime())) {
                        return null;
                    }
                    value.expiresAtNanos = System.nanoTime() + timeoutNanos;
                    return value;
                });
        if (externalInstance == null) {
            throw new SfcJsonException(404, "实例不存在或已过期：" + normalizedInstanceId);
        }
        return Math.max(1, TimeUnit.NANOSECONDS.toSeconds(timeoutNanos));
    }

    /**
     * 注销外部实例。
     *
     * @param serviceId  服务名
     * @param instanceId 实例 ID
     */
    public void deregisterExternal(String serviceId, String instanceId) {
        String normalizedServiceId = trimmed(serviceId);
        String normalizedInstanceId = trimmed(instanceId);
        if (!hasText(normalizedServiceId) || !hasText(normalizedInstanceId)) {
            return;
        }
        Map<String, ExternalInstance> instances = externalRegistry.get(normalizedServiceId);
        if (instances == null) {
            return;
        }
        instances.remove(normalizedInstanceId);
        if (instances.isEmpty()) {
            externalRegistry.remove(normalizedServiceId, instances);
        }
        log.info("[服务注销] 外部实例已移除 serviceId={} instanceId={}", normalizedServiceId, normalizedInstanceId);
    }

    /**
     * 清理已过期的外部实例。由 Spring 定时调度。
     */
    @org.springframework.scheduling.annotation.Scheduled(
            fixedDelayString = "${sys.service.local-registry.cleanup-interval:10s}")
    public void expireExternalInstances() {
        if (closed) {
            return;
        }
        long now = System.nanoTime();
        externalRegistry.forEach((serviceId, instances) -> {
            instances.entrySet().removeIf(entry -> {
                boolean expired = isExpired(entry.getValue(), now);
                if (expired) {
                    log.info("[服务过期] 已移除 serviceId={} instanceId={}", serviceId, entry.getKey());
                }
                return expired;
            });
            if (instances.isEmpty()) {
                externalRegistry.remove(serviceId, instances);
            }
        });
    }

    /**
     * 清空进程内注册表与外部实例表。
     */
    @Override
    public void close() {
        closed = true;
        int size = registry.size() + externalRegistry.size();
        registry.clear();
        externalRegistry.clear();
        log.info("[服务注册] 进程内注册表已清空，原有服务数量：{}", size);
    }

    /**
     * 更新宿主实例状态：{@code UP} 重新登记，{@code DOWN} 注销。
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
     * 查询宿主实例状态。
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
     * 按服务名列出实例，合并宿主实例与未过期的外部实例。
     *
     * @param serviceId 服务名
     * @return 实例列表，无匹配时返回空列表
     */
    @Override
    public List<ServiceInstance> getInstances(String serviceId) {
        if (!hasText(serviceId)) {
            return Collections.emptyList();
        }
        String normalizedServiceId = serviceId.trim();
        Map<String, ServiceInstance> result = new LinkedHashMap<>();

        Map<String, ServiceInstance> internalInstances = registry.get(normalizedServiceId);
        if (internalInstances != null) {
            result.putAll(internalInstances);
        }

        Map<String, ExternalInstance> externalInstances = externalRegistry.get(normalizedServiceId);
        if (externalInstances != null) {
            long now = System.nanoTime();
            externalInstances.forEach((instanceId, externalInstance) -> {
                if (isExpired(externalInstance, now)) {
                    externalInstances.remove(instanceId, externalInstance);
                    log.info("[服务过期] 已移除 serviceId={} instanceId={}", normalizedServiceId, instanceId);
                    return;
                }
                result.putIfAbsent(instanceId, externalInstance.instance);
            });
            if (externalInstances.isEmpty()) {
                externalRegistry.remove(normalizedServiceId, externalInstances);
            }
        }
        return result.isEmpty() ? Collections.emptyList() : new ArrayList<>(result.values());
    }

    /**
     * 列出已知服务名，包含存在未过期实例的外部服务。
     *
     * @return 服务名列表
     */
    @Override
    public List<String> getServices() {
        Set<String> services = new LinkedHashSet<>(registry.keySet());
        long now = System.nanoTime();
        externalRegistry.forEach((serviceId, instances) -> {
            instances.entrySet().removeIf(entry -> isExpired(entry.getValue(), now));
            if (instances.isEmpty()) {
                externalRegistry.remove(serviceId, instances);
            } else {
                services.add(serviceId);
            }
        });
        return new ArrayList<>(services);
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
     * 获取配置的心跳超时纳秒数，非法配置回退默认值。
     *
     * @return 心跳超时纳秒数
     */
    private long heartbeatTimeoutNanos() {
        Duration timeout = sysProperties == null
                || sysProperties.getService() == null
                || sysProperties.getService().getLocalRegistry() == null
                ? null
                : sysProperties.getService().getLocalRegistry().getHeartbeatTimeout();
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            timeout = DEFAULT_HEARTBEAT_TIMEOUT;
        }
        return timeout.toNanos();
    }

    /**
     * 判断外部实例是否已过期。
     *
     * @param externalInstance 外部实例
     * @param now              当前纳秒时间
     * @return 已过期时返回 true
     */
    private static boolean isExpired(ExternalInstance externalInstance, long now) {
        return externalInstance.expiresAtNanos - now <= 0;
    }

    /**
     * 校验必填文本字段。
     *
     * @param value     字段值
     * @param fieldName 字段名
     * @param maxLength 最大长度
     */
    private static void validateText(String value, String fieldName, int maxLength) {
        if (!hasText(value)) {
            throw new SfcJsonException(400, fieldName + " 不能为空");
        }
        if (value.length() > maxLength) {
            throw new SfcJsonException(400, fieldName + " 长度不能超过 " + maxLength);
        }
    }

    /**
     * 校验并复制实例元数据。
     *
     * @param metadata 原始元数据
     * @return 不可变元数据副本
     */
    private static Map<String, String> validateMetadata(Map<String, String> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return Map.of();
        }
        if (metadata.size() > MAX_METADATA_ENTRIES) {
            throw new SfcJsonException(400, "metadata 条目数不能超过 " + MAX_METADATA_ENTRIES);
        }
        Map<String, String> result = new LinkedHashMap<>();
        metadata.forEach((key, value) -> {
            String normalizedKey = trimmed(key);
            if (!hasText(normalizedKey) || normalizedKey.length() > MAX_METADATA_KEY_LENGTH) {
                throw new SfcJsonException(400, "metadata key 不能为空且长度不能超过 " + MAX_METADATA_KEY_LENGTH);
            }
            if (value == null || value.length() > MAX_METADATA_VALUE_LENGTH) {
                throw new SfcJsonException(400, "metadata value 不能为 null 且长度不能超过 " + MAX_METADATA_VALUE_LENGTH);
            }
            result.put(normalizedKey, value);
        });
        return Map.copyOf(result);
    }

    /**
     * 去除字符串首尾空白，null 安全。
     *
     * @param value 原始字符串
     * @return 去空白后的字符串，null 时返回 null
     */
    private static String trimmed(String value) {
        return value == null ? null : value.trim();
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

    /**
     * 外部实例条目：实例快照与过期时间。
     */
    private static final class ExternalInstance {
        /** 实例快照 */
        private final ServiceInstance instance;

        /** 过期时间（System.nanoTime） */
        private volatile long expiresAtNanos;

        /**
         * 创建外部实例条目。
         *
         * @param instance      实例快照
         * @param expiresAtNanos 过期时间（System.nanoTime）
         */
        private ExternalInstance(ServiceInstance instance, long expiresAtNanos) {
            this.instance = instance;
            this.expiresAtNanos = expiresAtNanos;
        }
    }
}
