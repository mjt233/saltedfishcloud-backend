package com.xiaotao.saltedfishcloud.service.registry;

import com.sfc.cloud.model.ServiceDefinition;
import com.sfc.cloud.model.ServiceInstance;
import com.sfc.cloud.registry.ServiceDiscovery;
import com.sfc.cloud.registry.ServiceRegistry;
import com.xiaotao.saltedfishcloud.config.SysProperties;
import com.sfc.cloud.exception.SfcJsonException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 进程内服务注册与发现实现（{@code sys.service.registry-provider: local}，默认值）。
 * <p>
 * <b>核心规则</b>：未使用 Nacos ⇒ 未启用微服务模式 ⇒ 系统处于完整单体 / 集群状态。此状态下的注册关系只在当前进程内维护，
 * 因此本实现刻意<b>不实现任何跨进程语义</b>：无 TTL、无续约、无死实例清理、无变更通知、无主动健康检查
 * —— 这些都是 Nacos 存在的意义，不是内存实现的缺陷。"服务列表"只是进程内视图，集群节点信息仍由
 * {@code GET /api/cluster/listNodes}（{@code ClusterService}）回答，两者<b>职责分离，不合并</b>。
 * <p>
 * <b>硬约束</b>：不注入任何既有功能组件（无 {@code CacheService} / {@code MQService}，也不包装 {@code ClusterService}），
 * 只使用一个自持的 {@link ConcurrentHashMap}。唯一的注入项是配置类 {@link SysProperties}：因为"发现侧没有 group 参数"
 * 是 SPI 契约的固有属性（{@link ServiceDiscovery#listInstances(String)} 只接收服务名），发现时必须从配置取 group 拼键。
 * <p>
 * <b>键结构</b>：{@code {group}:{serviceName} -> Map<instanceId, ServiceInstance>}。
 * {@code namespace} <b>刻意不参与</b>键结构——内存 Map 即单一命名空间。
 * <p>
 * <b>一个刻意保留的坑</b>：注册侧取 {@link ServiceDefinition#getGroup()}、发现侧取配置 {@code sys.service.registry-group}，
 * 与 Nacos 的调用模型同构。因此"注册与发现用的 group 不是同一个值"时会查不到实例——这正是要提前暴露的认知
 * （对应 Nacos 的 {@code DEFAULT_GROUP} 错配问题）。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "sys.service", name = "registry-provider", havingValue = "local", matchIfMissing = true)
public class LocalServiceRegistry implements ServiceRegistry, ServiceDiscovery {
    /**
     * 配置项 {@code sys.service.registry-group} 为空时的兜底分组名。
     * <p>
     * 注册与发现两侧都读同一个配置，因此无论配置取何值都不会出现"注册进去了却查不到"；
     * 兜底只是为了避免出现 {@code null:serviceName} 这类难以阅读的键。
     */
    private static final String DEFAULT_GROUP = "SFC";

    /** 分组与服务名之间的分隔符 */
    private static final String GROUP_SEPARATOR = ":";

    /** 注册表：{@code {group}:{serviceName}} -> ({@code instanceId} -> 实例) */
    private final Map<String, Map<String, ServiceInstance>> registry = new ConcurrentHashMap<>();

    /** 系统配置，仅用于读取注册分组（发现侧没有 group 参数，只能取配置） */
    private final SysProperties sysProperties;

    /** 自身登记的服务键，注销时用于确保只删自己 */
    private volatile String selfKey;

    /** 自身登记的实例 ID，注销时用于确保只删自己 */
    private volatile String selfInstanceId;

    public LocalServiceRegistry(SysProperties sysProperties) {
        this.sysProperties = sysProperties;
    }

    /**
     * 登记实例。
     * <p>
     * 入参<b>真实入库</b>（不做任何丢弃或静默忽略），重复调用幂等（按 {@code instanceId} 覆盖）。
     *
     * @param definition 自身服务描述，{@code serviceName} 不能为空
     * @param instance   自身实例信息
     * @throws SfcJsonException 服务名为空或入参为 null 时抛出
     */
    @Override
    public void register(ServiceDefinition definition, ServiceInstance instance) {
        if (definition == null || instance == null || !hasText(definition.getServiceName())) {
            throw new SfcJsonException("服务注册失败：ServiceDefinition.serviceName 与 ServiceInstance 均不能为空");
        }
        String key = buildKey(definition.getGroup(), definition.getServiceName());
        String instanceId = instanceIdOf(instance);
        Map<String, ServiceInstance> instances = registry.get(key);
        if (instances == null) {
            // 组内首个实例：用 putIfAbsent 保证并发下只创建一张内层表
            Map<String, ServiceInstance> created = new ConcurrentHashMap<>();
            Map<String, ServiceInstance> existing = registry.putIfAbsent(key, created);
            instances = existing == null ? created : existing;
        }
        instances.put(instanceId, instance);
        this.selfKey = key;
        this.selfInstanceId = instanceId;
        log.info("[服务注册] 进程内注册表已登记 key={} instanceId={}", key, instanceId);
    }

    /**
     * 注销<b>自身</b>实例：只删除 {@link #register(ServiceDefinition, ServiceInstance)} 登记的那一条，
     * 不触碰同组的其它实例（当前实现中不会有其它实例，但语义必须正确）。
     */
    @Override
    public void deregister() {
        String key = this.selfKey;
        String instanceId = this.selfInstanceId;
        if (key == null || instanceId == null) {
            return;
        }
        Map<String, ServiceInstance> instances = registry.get(key);
        if (instances != null) {
            instances.remove(instanceId);
            // 组内已无实例时连同服务键一起清掉，避免残留空 Map
            if (instances.isEmpty()) {
                registry.remove(key, instances);
            }
        }
        this.selfKey = null;
        this.selfInstanceId = null;
        log.info("[服务注销] 进程内注册表已移除 key={} instanceId={}", key, instanceId);
    }

    /**
     * 进程内注册表必然可用：没有远端连接可断开，健康检查也是不必要的（进程即生命周期）。
     *
     * @return 恒为 true
     */
    @Override
    public boolean isAvailable() {
        return true;
    }

    /**
     * 按服务名列出实例。分组取配置 {@code sys.service.registry-group}（发现侧没有 group 参数）。
     *
     * @param serviceName 服务名，为空时返回空列表
     * @return 实例列表，无匹配时返回空列表
     */
    @Override
    public List<ServiceInstance> listInstances(String serviceName) {
        if (!hasText(serviceName)) {
            return Collections.emptyList();
        }
        Map<String, ServiceInstance> instances = registry.get(buildKey(configuredGroup(), serviceName.trim()));
        return instances == null ? Collections.emptyList() : new ArrayList<>(instances.values());
    }

    /**
     * 列出当前配置分组下所有已知服务名。
     *
     * @return 服务名列表（字典序），无匹配时返回空列表
     */
    @Override
    public List<String> listServiceNames() {
        String prefix = buildKey(configuredGroup(), "");
        TreeSet<String> names = new TreeSet<>();
        for (String key : registry.keySet()) {
            if (key.startsWith(prefix)) {
                names.add(key.substring(prefix.length()));
            }
        }
        return new ArrayList<>(names);
    }

    /**
     * 释放进程内注册表。{@code local} provider 无需注销（进程退出即清空），这里只是把资源释放干净。
     */
    @Override
    public void close() {
        int size = registry.size();
        registry.clear();
        this.selfKey = null;
        this.selfInstanceId = null;
        log.info("[服务注册] 进程内注册表已清空，原有服务键数量：{}", size);
    }

    /**
     * 拼装注册表键：{@code {group}:{serviceName}}。
     *
     * @param group       分组，为空时取 {@link #DEFAULT_GROUP}
     * @param serviceName 服务名
     * @return 注册表键
     */
    private static String buildKey(String group, String serviceName) {
        return normalizeGroup(group) + GROUP_SEPARATOR + serviceName;
    }

    /**
     * 取当前配置的注册分组。
     *
     * @return 配置值，为空时取 {@link #DEFAULT_GROUP}
     */
    private String configuredGroup() {
        return normalizeGroup(sysProperties.getService().getRegistryGroup());
    }

    /**
     * 分组名归一化：去空白，空值退化为 {@link #DEFAULT_GROUP}。
     *
     * @param group 原始分组名
     * @return 归一化后的分组名
     */
    private static String normalizeGroup(String group) {
        return hasText(group) ? group.trim() : DEFAULT_GROUP;
    }

    /**
     * 取实例 ID：优先使用调用方给出的 {@link ServiceInstance#getInstanceId()}，
     * 为空时退化为 {@code {host}:{port}}（与 {@link ServiceIdentityResolver} 的生成规则一致）。
     *
     * @param instance 实例信息
     * @return 实例 ID
     */
    private static String instanceIdOf(ServiceInstance instance) {
        String instanceId = instance.getInstanceId();
        return hasText(instanceId) ? instanceId : instance.getHost() + GROUP_SEPARATOR + instance.getPort();
    }

    /**
     * 判断字符串非 null 且含非空白字符。本类自带最小实现，不引入任何工具类依赖。
     *
     * @param str 待判断字符串
     * @return 非空白时返回 true
     */
    private static boolean hasText(String str) {
        return str != null && !str.isBlank();
    }
}
