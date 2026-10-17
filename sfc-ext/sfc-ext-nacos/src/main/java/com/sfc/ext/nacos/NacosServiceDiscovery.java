package com.sfc.ext.nacos;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.ListView;
import com.sfc.cloud.model.ServiceInstance;
import com.sfc.cloud.registry.ServiceDiscovery;
import com.xiaotao.saltedfishcloud.config.SysProperties;
import com.sfc.common.exception.SfcJsonException;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

/**
 * 基于 Nacos 的服务发现实现（{@code sys.service.registry-provider: nacos}）。
 * <p>
 * <b>group 来自配置</b>：{@link ServiceDiscovery} 的查询接口只接收服务名，因此 group 只能取配置
 * {@code sys.service.registry-group}（兜底 {@value #DEFAULT_GROUP}），namespace 取插件配置
 * {@code sys.service.nacos.namespace}。这与 {@code local} provider 的取值方式一致；"注册与发现用了不同 group /
 * namespace"时会查不到实例，与 Nacos 自身的 {@code DEFAULT_GROUP} 错配问题同构。
 * <p>
 * <b>为什么用 getAllInstances 而不是 selectInstances</b>：SPI 的语义是"查询一次"（没有变更通知），本应使用直连查询，
 * 但实测（nacos-client 3.2.4）在客户端冷启动、尚未建立订阅时，
 * {@code selectInstances(serviceName, group, subscribe=false, healthy=true)} <b>恒返回空列表</b>，且不会退化为服务端查询；
 * 而 {@code getAllInstances(serviceName, group)} 首次调用即返回正确结果，又不产生订阅状态，故采用后者。
 * 由于 {@code getAllInstances} 不过滤健康状态，这里在客户端侧按 {@code healthy + enabled} 过滤
 * （{@code enabled} 对应控制台的实例禁用开关）。
 */
@Slf4j
public class NacosServiceDiscovery implements ServiceDiscovery {

    /** group 为空时的兜底分组名，与 {@code local} provider 及 {@link NacosServiceRegistry} 保持一致 */
    private static final String DEFAULT_GROUP = "SFC";

    /** 服务名分页大小：取客户端侧的保守值，避免超过服务端分页上限 */
    private static final int PAGE_SIZE = 100;

    /** 分页拉取的防御性上限，避免服务端异常时死循环 */
    private static final int MAX_PAGE = 100;

    /** Nacos 客户端 */
    private final NamingService namingService;

    /** 系统配置，仅用于读取注册分组（发现侧没有 group 参数） */
    private final SysProperties sysProperties;

    public NacosServiceDiscovery(NamingService namingService, SysProperties sysProperties) {
        this.namingService = namingService;
        this.sysProperties = sysProperties;
    }

    /**
     * 按服务名列出<b>健康</b>实例。
     *
     * @param serviceName 服务名，为空时返回空列表
     * @return 实例列表，无匹配时返回空列表
     * @throws SfcJsonException Nacos 服务端返回错误时抛出
     */
    @Override
    public List<ServiceInstance> listInstances(String serviceName) {
        if (!hasText(serviceName)) {
            return Collections.emptyList();
        }
        String name = serviceName.trim();
        String group = configuredGroup();
        List<Instance> instances;
        try {
            // 不要改用 selectInstances(name, group, false, true)：实测在 3.2.4 下恒为空列表
            instances = namingService.getAllInstances(name, group);
        } catch (NacosException e) {
            String msg = "Nacos 服务查询失败：serviceName=" + name + " group=" + group + "。原因：" + e.getMessage();
            log.error("[服务发现] {}", msg, e);
            throw new SfcJsonException(msg);
        }
        if (instances == null || instances.isEmpty()) {
            return Collections.emptyList();
        }
        List<ServiceInstance> result = new ArrayList<>(instances.size());
        for (Instance instance : instances) {
            // getAllInstances 返回全部实例（含不健康 / 被禁用），此处对齐"只列出可用实例"的语义
            if (!instance.isHealthy() || !instance.isEnabled()) {
                log.debug("[服务发现] 跳过不可用实例 instanceId={} healthy={} enabled={}",
                        instance.getInstanceId(), instance.isHealthy(), instance.isEnabled());
                continue;
            }
            result.add(toServiceInstance(name, instance));
        }
        log.debug("[服务发现] serviceName={} group={} 命中 {} 个可用实例", name, group, result.size());
        return result;
    }

    /**
     * 列出当前配置分组下所有已知服务名（分页拉全）。
     *
     * @return 服务名列表，无匹配时返回空列表
     * @throws SfcJsonException Nacos 服务端返回错误时抛出
     */
    @Override
    public List<String> listServiceNames() {
        String group = configuredGroup();
        List<String> names = new ArrayList<>();
        for (int pageNo = 1; pageNo <= MAX_PAGE; pageNo++) {
            ListView<String> view;
            try {
                view = namingService.getServicesOfServer(pageNo, PAGE_SIZE, group);
            } catch (NacosException e) {
                String msg = "Nacos 服务名列表查询失败：group=" + group + " pageNo=" + pageNo + "。原因：" + e.getMessage();
                log.error("[服务发现] {}", msg, e);
                throw new SfcJsonException(msg);
            }
            List<String> data = view == null ? null : view.getData();
            if (data == null || data.isEmpty()) {
                break;
            }
            names.addAll(data);
            if (data.size() < PAGE_SIZE) {
                break;
            }
        }
        log.debug("[服务发现] group={} 共 {} 个服务名", group, names.size());
        return names;
    }

    /**
     * 可用性判断与 {@link NacosServiceRegistry#isAvailable()} 同源（同一个客户端实例）。
     *
     * @return 服务端状态为 UP 时返回 true
     */
    @Override
    public boolean isAvailable() {
        try {
            return "UP".equalsIgnoreCase(namingService.getServerStatus());
        } catch (Exception e) {
            log.warn("[服务发现] 获取 Nacos 服务端状态失败：{}", e.getMessage());
            return false;
        }
    }

    /**
     * 把 Nacos 实例模型转换为本项目的 {@link ServiceInstance}。
     *
     * @param serviceName   服务名（Nacos 的查询结果不携带，需由调用方补全）
     * @param nacosInstance Nacos 实例
     * @return 项目内的实例模型
     */
    private static ServiceInstance toServiceInstance(String serviceName, Instance nacosInstance) {
        String ip = nacosInstance.getIp();
        return ServiceInstance.builder()
                .serviceName(serviceName)
                .instanceId(instanceIdOf(nacosInstance, ip))
                .host(ip)
                .port(nacosInstance.getPort())
                .healthy(nacosInstance.isHealthy())
                .metadata(nacosInstance.getMetadata() == null ? null : new HashMap<>(nacosInstance.getMetadata()))
                .build();
    }

    /**
     * 取实例 ID：优先使用 Nacos 生成的 {@code instanceId}（服务端分配），为空时退化为 {@code {ip}:{port}}。
     *
     * @param nacosInstance Nacos 实例
     * @param ip            实例 IP
     * @return 实例 ID
     */
    private static String instanceIdOf(Instance nacosInstance, String ip) {
        String instanceId = nacosInstance.getInstanceId();
        return hasText(instanceId) ? instanceId : ip + ":" + nacosInstance.getPort();
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
     * 判断字符串非 null 且含非空白字符。
     *
     * @param str 待判断字符串
     * @return 非空白时返回 true
     */
    private static boolean hasText(String str) {
        return str != null && !str.isBlank();
    }
}
