package com.xiaotao.saltedfishcloud.service.registry;

import com.sfc.cloud.model.ServiceInstance;
import com.xiaotao.saltedfishcloud.config.SysProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * 服务身份解析器：解析"本服务实例在哪"，生成用于注册的 {@link ServiceInstance}。
 * <p>
 * <b>设计原则</b>：调用方负责解析自身身份，provider 只负责注册中心协议。因此本类与 provider 无关——
 * {@code local} 与 {@code nacos} 两种 provider 共用它，provider 无需重复实现地址解析逻辑。
 * <p>
 * <b>host 解析</b>：配置 {@code sys.service.advertise-address} 优先；留空时自动解析本机主机名。
 * 多网卡 / 容器 / NAS 环境下自动解析出的地址可能不可达，此时需显式配置。注意不能复用
 * {@code ClusterNodeInfo.ip}——它把所有网卡地址用 {@code ;} 拼接，是"用于展示"的形态，不可作为注册地址。
 * <p>
 * <b>port 解析</b>：优先读取 web 容器真正绑定端口（{@code local.server.port}，由 SpringBoot 在 web 容器初始化后写入），
 * 其次读配置 {@code server.port}。因此本类<b>必须在 {@code ApplicationReadyEvent}（或更晚）调用</b>：
 * 在 Bean 初始化早期调用只能拿到配置值，{@code server.port: 0} 时会把 0 当作端口登记出去。
 */
@Slf4j
@Component
public class ServiceIdentityResolver {
    /** 主机名自动解析失败时的兜底地址 */
    private static final String FALLBACK_HOST = "127.0.0.1";

    /** SpringBoot 在 web 容器初始化后写入的真实绑定端口属性 */
    private static final String ACTUAL_PORT_PROPERTY = "local.server.port";

    /** 配置的 HTTP 端口属性 */
    private static final String CONFIGURED_PORT_PROPERTY = "server.port";

    /** 系统配置：提供 serviceName 与 advertiseAddress */
    private final SysProperties sysProperties;

    /** 运行环境：用于读取真实绑定端口与配置端口 */
    private final Environment environment;

    public ServiceIdentityResolver(SysProperties sysProperties, Environment environment) {
        this.sysProperties = sysProperties;
        this.environment = environment;
    }

    /**
     * 解析本机服务实例。
     * <p>
     * {@code instanceId} 取 {@code {host}:{port}}：与 Nacos {@code ephemeral} 实例的标识形态同构，
     * 且进程重启后复用同一个键，避免"自己看见两个自己"的窗口。
     *
     * @return 本机服务实例，serviceName 取自配置，host / port / instanceId 已解析完毕
     */
    public ServiceInstance resolve() {
        String host = resolveHost();
        int port = resolvePort();
        String instanceId = host + ":" + port;
        log.info("[服务身份解析] serviceName={} host={} port={} instanceId={}",
                sysProperties.getService().getServiceName(), host, port, instanceId);
        return ServiceInstance.builder()
                .serviceName(sysProperties.getService().getServiceName())
                .instanceId(instanceId)
                .host(host)
                .port(port)
                .healthy(true)
                // 无消费者，不提前造数据：metadata 留空
                .metadata(null)
                .build();
    }

    /**
     * 解析对外可达 host：{@code sys.service.advertise-address} 优先，留空时自动解析本机主机名。
     *
     * @return 主机名或 IP
     */
    private String resolveHost() {
        String advertiseAddress = sysProperties.getService().getAdvertiseAddress();
        if (advertiseAddress != null && !advertiseAddress.isBlank()) {
            return advertiseAddress.trim();
        }
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            log.warn("[服务身份解析] 本机主机名解析失败，退化为 {}：{}", FALLBACK_HOST, e.getMessage());
            return FALLBACK_HOST;
        }
    }

    /**
     * 解析 HTTP 端口：真实绑定端口优先，其次配置值。
     *
     * @return 端口，两者都取不到时返回 0
     */
    private int resolvePort() {
        Integer actualPort = readPort(ACTUAL_PORT_PROPERTY);
        if (actualPort != null && actualPort > 0) {
            return actualPort;
        }
        Integer configuredPort = readPort(CONFIGURED_PORT_PROPERTY);
        if (configuredPort != null && configuredPort > 0) {
            return configuredPort;
        }
        log.warn("[服务身份解析] 未能解析出 HTTP 端口，{} 与 {} 均不可用，将登记为 0",
                ACTUAL_PORT_PROPERTY, CONFIGURED_PORT_PROPERTY);
        return 0;
    }

    /**
     * 读取非负整数配置项。
     *
     * @param property 配置项名称
     * @return 配置值，未配置或无法解析时返回 null
     */
    private Integer readPort(String property) {
        String value = environment.getProperty(property);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException e) {
            log.warn("[服务身份解析] 配置项 {} 不是合法端口：{}", property, value);
            return null;
        }
    }
}
