package com.sfc.ext.nacos;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.sfc.cloud.model.ServiceDefinition;
import com.sfc.cloud.model.ServiceInstance;
import com.sfc.cloud.registry.ServiceRegistry;
import com.sfc.ext.nacos.config.SysNacosProperties;
import com.sfc.common.exception.SfcJsonException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;

import java.util.HashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 基于 Nacos 的服务注册实现（{@code sys.service.registry-provider: nacos}）。
 * <p>
 * <b>职责划分</b>：与 SPI 约定一致——调用方（{@code ServiceRegistryInitializer} + {@code ServiceIdentityResolver}）
 * 负责解析"我是谁 / 我在哪"，本类只负责把二者翻译成 Nacos 的注册协议，不重复实现地址解析。
 * <p>
 * <b>namespace 与 group 的取值差异（刻意保留）</b>：Nacos 客户端的 namespace 在创建时固定
 * （{@link NamingService} 没有任何 per-call 的 namespace 参数），因此命名空间只能取插件配置
 * {@code sys.service.nacos.namespace}，{@link ServiceDefinition#getNamespace()} 在本实现中<b>被忽略</b>；
 * group 则相反，注册侧取 {@link ServiceDefinition#getGroup()}。这与 {@code local} provider
 * "注册侧取 definition、发现侧取配置" 的不对称完全一致，"注册与发现用了不同 group"时会互相看不见。
 * <p>
 * <b>续约</b>：实例以 {@code ephemeral=true} 注册，续约由 Nacos 客户端内置心跳完成——对应 SPI 刻意不提供
 * {@code renew()} 的分工（见 {@link ServiceRegistry} 类注释）。
 * <p>
 * <b>注销</b>：{@link #deregister()} 只注销 {@link #register(ServiceDefinition, ServiceInstance)} 登记的自身实例；
 * {@link #close()} 先注销再关闭客户端；{@link DisposableBean#destroy()} 委托 {@code close()}，
 * 因为 Spring 对 {@code @Bean} 的销毁方法推断不保证覆盖插件场景（设计决策 6：注销不得只寄托在 {@code close()} 上）。
 */
@Slf4j
public class NacosServiceRegistry implements ServiceRegistry, DisposableBean {

    /**
     * group 为空时的兜底分组名，与 {@code local} provider 的 {@code DEFAULT_GROUP} 保持一致。
     * <p>
     * 这里刻意<b>不</b>使用 Nacos 自带的 {@code DEFAULT_GROUP}：注册与发现两侧都经同一套归一化，
     * 只要两侧配置一致就不会出现"注册进去了却查不到"。
     */
    private static final String DEFAULT_GROUP = "SFC";

    /** Nacos 客户端 */
    private final NamingService namingService;

    /** 插件配置（提供 clusterName） */
    private final SysNacosProperties properties;

    /**
     * 上一次成功登记的服务名。SPI 的 {@link #deregister()} 不带参数，注销依赖这里记录的自身身份，
     * 从而保证"只注销自己、不可能误删其它实例"。
     */
    private volatile String registeredServiceName;

    /** 上一次成功登记的分组 */
    private volatile String registeredGroup;

    /** 上一次成功登记的实例 IP */
    private volatile String registeredIp;

    /** 上一次成功登记的实例端口 */
    private volatile int registeredPort;

    /** 关闭标记：Spring 可能同时走 {@link DisposableBean#destroy()} 与推断出的 {@code close()}，用它保证只关一次 */
    private final AtomicBoolean closed = new AtomicBoolean(false);

    public NacosServiceRegistry(NamingService namingService, SysNacosProperties properties) {
        this.namingService = namingService;
        this.properties = properties;
    }

    /**
     * 注册实例。重复调用幂等：Nacos 以 {@code ip:port:clusterName} 为实例键，重复注册即覆盖。
     *
     * @param definition 自身服务描述，{@code serviceName} 不能为空
     * @param instance   自身实例信息，{@code host} 与 {@code port} 必须有效
     * @throws SfcJsonException 入参不合法或 Nacos 服务端返回错误时抛出
     */
    @Override
    public void register(ServiceDefinition definition, ServiceInstance instance) {
        if (definition == null || instance == null || !hasText(definition.getServiceName())) {
            throw new SfcJsonException("服务注册失败：ServiceDefinition.serviceName 与 ServiceInstance 均不能为空");
        }
        if (!hasText(instance.getHost()) || instance.getPort() <= 0) {
            throw new SfcJsonException("服务注册失败：实例地址无效 host=" + instance.getHost() + " port=" + instance.getPort());
        }
        String serviceName = definition.getServiceName().trim();
        String group = normalizeGroup(definition.getGroup());
        Instance nacosInstance = buildNacosInstance(instance);
        try {
            namingService.registerInstance(serviceName, group, nacosInstance);
        } catch (NacosException e) {
            String msg = "Nacos 服务注册失败：serviceName=" + serviceName + " group=" + group + " instance="
                    + instance.getHost() + ":" + instance.getPort()
                    + "。请检查 sys.service.nacos.server-addr 与 Nacos 服务端状态。原因：" + e.getMessage();
            log.error("[服务注册] {}", msg, e);
            throw new SfcJsonException(msg);
        }

        this.registeredServiceName = serviceName;
        this.registeredGroup = group;
        this.registeredIp = instance.getHost().trim();
        this.registeredPort = instance.getPort();
        log.info("[服务注册] Nacos 注册完成 serviceName={} group={} instance={}:{} namespace={} cluster={} ephemeral=true",
                serviceName, group, this.registeredIp, this.registeredPort,
                hasText(properties.getNamespace()) ? properties.getNamespace() : "<public>", properties.getClusterName());
    }

    /**
     * 注销<b>自身</b>实例：只注销 {@link #register(ServiceDefinition, ServiceInstance)} 登记的那一条。
     * <p>
     * 注销是<b>终止性</b>操作：无论调用成功与否都会清空自身登记，避免关闭流程反复重试同一个失败调用。
     * 即使服务端此刻不可达，临时实例也会在客户端心跳停止后被服务端自动清理，不会留下永久残留。
     * 未注册时静默返回（幂等）。
     *
     * @throws SfcJsonException Nacos 服务端返回错误时抛出
     */
    @Override
    public void deregister() {
        String serviceName = this.registeredServiceName;
        if (serviceName == null) {
            return;
        }
        String group = this.registeredGroup;
        String ip = this.registeredIp;
        int port = this.registeredPort;
        clearRegistered();
        try {
            namingService.deregisterInstance(serviceName, group, ip, port);
        } catch (NacosException e) {
            String msg = "Nacos 服务注销失败：serviceName=" + serviceName + " group=" + group + " instance=" + ip + ":" + port
                    + "。临时实例将由服务端心跳超时自动清理。原因：" + e.getMessage();
            log.error("[服务注销] {}", msg, e);
            throw new SfcJsonException(msg);
        }
        log.info("[服务注销] Nacos 已注销自身实例 serviceName={} group={} instance={}:{}", serviceName, group, ip, port);
    }

    /**
     * 以 Nacos 客户端报告的服务端状态判断可用性：只有客户端明确报告 {@code UP} 才算可用。
     * <p>
     * 与 {@code local} provider 恒为 {@code true} 不同，这是本实现与远端注册中心绑定后的真实差异。
     *
     * @return 服务端状态为 UP 时返回 true
     */
    @Override
    public boolean isAvailable() {
        try {
            String status = namingService.getServerStatus();
            boolean up = "UP".equalsIgnoreCase(status);
            if (!up) {
                log.debug("[服务注册] Nacos 服务端当前状态：{}", status);
            }
            return up;
        } catch (Exception e) {
            log.warn("[服务注册] 获取 Nacos 服务端状态失败：{}", e.getMessage());
            return false;
        }
    }

    /**
     * 释放 provider 资源：先注销自身实例（best-effort，失败只记录日志），再关闭 Nacos 客户端。
     * <p>
     * 顺序不可颠倒——客户端一旦关闭，注销必然失败。
     */
    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        try {
            deregister();
        } catch (Exception e) {
            log.warn("[服务注册] 关闭时注销自身实例失败（临时实例将由服务端心跳超时自动清理）：{}", e.getMessage());
        }
        try {
            namingService.shutDown();
        } catch (Exception e) {
            log.warn("[服务注册] 关闭 Nacos 客户端失败：{}", e.getMessage());
        }
        log.info("[服务注册] Nacos provider 已关闭");
    }

    /**
     * Spring 销毁回调，委托给 {@link #close()}。
     */
    @Override
    public void destroy() {
        close();
    }

    /**
     * 把项目内的实例模型翻译为 Nacos 的实例模型。
     * <p>
     * 实例 ID 刻意<b>不</b>设置，交给 Nacos 服务端生成：客户端内部的注册与心跳都按 {@code ip:port} 建键，
     * 自造 instanceId 只会引入不一致。
     *
     * @param instance 项目内的实例信息
     * @return Nacos 实例
     */
    private Instance buildNacosInstance(ServiceInstance instance) {
        Instance nacosInstance = new Instance();
        nacosInstance.setIp(instance.getHost().trim());
        nacosInstance.setPort(instance.getPort());
        nacosInstance.setWeight(1.0);
        // 临时实例的最终健康状态由服务端心跳判定，这里给出客户端声明的初始值
        nacosInstance.setHealthy(true);
        nacosInstance.setEnabled(true);
        // 临时实例：客户端内置心跳续约，也是阶段 4 验证"停服后实例自动消失"的前提
        nacosInstance.setEphemeral(true);
        nacosInstance.setClusterName(properties.getClusterName());
        if (instance.getMetadata() != null) {
            nacosInstance.setMetadata(new HashMap<>(instance.getMetadata()));
        }
        return nacosInstance;
    }

    /**
     * 清空自身登记信息。
     */
    private void clearRegistered() {
        this.registeredServiceName = null;
        this.registeredGroup = null;
        this.registeredIp = null;
        this.registeredPort = 0;
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
     * 判断字符串非 null 且含非空白字符。
     *
     * @param str 待判断字符串
     * @return 非空白时返回 true
     */
    private static boolean hasText(String str) {
        return str != null && !str.isBlank();
    }
}
