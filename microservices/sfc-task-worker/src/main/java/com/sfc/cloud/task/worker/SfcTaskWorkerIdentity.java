package com.sfc.cloud.task.worker;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.client.serviceregistry.Registration;
import org.springframework.core.env.Environment;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * 当前 worker 实例的身份信息：服务注册中心中的 {@code serviceId} 与实例地址 {@code host:port}。
 *
 * <p>身份优先取自 Spring Cloud 的 {@link Registration} bean：服务名取 {@code serviceId}，
 * 实例地址取注册信息中的 {@code host} 与 {@code port}。取不到注册信息时退化为本机地址并告警——
 * 该标识不在服务注册中心中，core 会因此把本实例认领的任务误判为失联并回收。</p>
 *
 * <p>注册信息在 Web 容器完成端口绑定后才最终确定，因此全部惰性解析（首次使用时求值）。</p>
 */
@Slf4j
public class SfcTaskWorkerIdentity {

    /**
     * 日志前缀
     */
    private static final String LOG_PREFIX = "[任务Worker]";

    /**
     * 兜底服务名
     */
    private static final String DEFAULT_SERVICE_NAME = "sfc-task-worker";

    /**
     * 兜底主机地址
     */
    private static final String FALLBACK_HOST = "127.0.0.1";

    /**
     * 兜底端口
     */
    private static final int FALLBACK_PORT = 8080;

    /**
     * 配置项
     */
    private final SfcTaskWorkerProperties properties;

    /**
     * Spring 环境
     */
    private final Environment environment;

    /**
     * 服务注册信息提供者
     */
    private final ObjectProvider<Registration> registrations;

    /**
     * 服务注册信息（惰性解析并缓存，未启用服务注册时为 null）
     */
    private volatile Registration registration;

    /**
     * 是否已尝试解析过服务注册信息
     */
    private volatile boolean registrationResolved;

    /**
     * 惰性解析并缓存的服务名
     */
    private volatile String serviceId;

    /**
     * 惰性解析并缓存的实例地址
     */
    private volatile String workerId;

    /**
     * 惰性解析并缓存的主机地址
     */
    private volatile String host;

    /**
     * 惰性解析并缓存的端口
     */
    private volatile int port = -1;

    /**
     * 构造身份信息（由自动装配创建）。
     *
     * @param properties    配置项
     * @param environment   Spring 环境
     * @param registrations 服务注册信息提供者，消费方未启用服务注册时为空
     */
    SfcTaskWorkerIdentity(SfcTaskWorkerProperties properties, Environment environment,
                          ObjectProvider<Registration> registrations) {
        this.properties = properties;
        this.environment = environment;
        this.registrations = registrations;
    }

    /**
     * 判断是否取到了服务注册中心中的实例信息。
     *
     * @return 取到 Registration 时返回 true
     */
    boolean isRegistered() {
        return resolveRegistration() != null;
    }

    /**
     * 判断服务注册信息是否已提供稳定的实例地址（host + 端口）。
     *
     * <p>注册信息在 Web 容器完成端口绑定后才完整，因此启动早期可能返回 {@code false}。</p>
     *
     * @return 注册信息完整时返回 true
     */
    synchronized boolean hasRegisteredInstance() {
        Registration resolved = resolveRegistration();
        return resolved != null && hasText(resolved.getHost()) && resolved.getPort() > 0;
    }

    /**
     * 获取服务名（服务注册中心中的 serviceId）。
     *
     * @return 服务名
     */
    public synchronized String getServiceId() {
        if (serviceId == null) {
            Registration resolved = resolveRegistration();
            if (resolved != null && hasText(resolved.getServiceId())) {
                serviceId = resolved.getServiceId().trim();
            } else {
                serviceId = resolveConfiguredServiceName();
            }
        }
        return serviceId;
    }

    /**
     * 获取实例地址（{@code host:port}）。
     *
     * <p>优先使用服务注册信息中的主机与端口；取不到注册信息时使用本机地址并告警。
     * 注册信息尚未完整（Web 容器端口还没回填）时返回临时地址但不缓存，待注册完成后重新解析。</p>
     *
     * @return 实例地址，形如 {@code 192.168.1.10:8080}
     */
    public synchronized String getWorkerId() {
        if (workerId != null) {
            return workerId;
        }
        Registration resolved = resolveRegistration();
        if (resolved == null) {
            workerId = getHost() + ":" + getPort();
            log.warn("{}未获取到服务注册信息（Registration），执行者身份使用本机地址 {}；"
                            + "该实例不在服务注册中心中，core 会因查不到执行者而把本实例认领的任务误判为失联并回收，"
                            + "请检查服务注册（discovery/registry）配置",
                    LOG_PREFIX, workerId);
            return workerId;
        }
        if (!hasRegisteredInstance()) {
            // 注册信息尚未完整：返回临时地址但不缓存，等注册完成后重新解析
            log.debug("{}服务注册信息尚未完整（host={}, port={}），暂不固定实例地址",
                    LOG_PREFIX, resolved.getHost(), resolved.getPort());
            return getHost() + ":" + getPort();
        }
        workerId = getHost() + ":" + getPort();
        log.info("{}执行者身份：serviceId={}, workerId={}", LOG_PREFIX, getServiceId(), workerId);
        return workerId;
    }

    /**
     * 获取实例主机地址：优先服务注册信息，缺失时取本机地址。
     *
     * <p>仅在服务注册信息缺失时缓存本机地址；注册信息存在但尚未回填 host 时只返回临时值，避免错误值被固定。</p>
     *
     * @return 主机地址
     */
    public synchronized String getHost() {
        if (host == null) {
            Registration resolved = resolveRegistration();
            if (resolved != null) {
                if (hasText(resolved.getHost())) {
                    host = resolved.getHost().trim();
                } else {
                    return resolveLocalHost();
                }
            } else {
                host = resolveLocalHost();
            }
        }
        return host;
    }

    /**
     * 获取实例端口：优先服务注册信息，缺失时取本机监听端口。
     *
     * <p>仅在服务注册信息缺失时缓存本机端口；注册信息存在但尚未回填端口时只返回临时值，避免错误值被固定。</p>
     *
     * @return 端口
     */
    public synchronized int getPort() {
        if (port <= 0) {
            Registration resolved = resolveRegistration();
            if (resolved != null) {
                if (resolved.getPort() > 0) {
                    port = resolved.getPort();
                } else {
                    return resolveLocalPort();
                }
            } else {
                port = resolveLocalPort();
            }
        }
        return port;
    }

    /**
     * 解析服务注册信息并缓存结果。
     *
     * @return 服务注册信息，未启用服务注册时返回 null
     */
    private synchronized Registration resolveRegistration() {
        if (!registrationResolved) {
            try {
                registration = registrations == null ? null : registrations.getIfUnique();
            } catch (Throwable e) {
                log.warn("{}解析服务注册信息失败，将按未注册实例处理: {}", LOG_PREFIX, describe(e));
                registration = null;
            }
            registrationResolved = true;
        }
        return registration;
    }

    /**
     * 解析配置项中声明的服务名。
     *
     * @return 服务名
     */
    private String resolveConfiguredServiceName() {
        String configured = properties.getServiceName();
        if (hasText(configured)) {
            return configured.trim();
        }
        String applicationName = environment == null ? null : environment.getProperty("spring.application.name");
        return hasText(applicationName) ? applicationName.trim() : DEFAULT_SERVICE_NAME;
    }

    /**
     * 解析本机地址。
     *
     * @return 主机地址
     */
    private String resolveLocalHost() {
        String configured = environment == null ? null : environment.getProperty("server.address");
        if (hasText(configured) && !"0.0.0.0".equals(configured.trim()) && !"::".equals(configured.trim())) {
            return configured.trim();
        }
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            log.warn("{}无法解析本机地址，将回退为 {}: {}", LOG_PREFIX, FALLBACK_HOST, e.getMessage());
            return FALLBACK_HOST;
        }
    }

    /**
     * 解析监听端口。
     *
     * @return 端口
     */
    private int resolveLocalPort() {
        try {
            Integer localPort = environment == null ? null : environment.getProperty("local.server.port", Integer.class);
            if (localPort != null && localPort > 0) {
                return localPort;
            }
        } catch (Exception e) {
            log.debug("{}读取 local.server.port 失败: {}", LOG_PREFIX, e.getMessage());
        }
        try {
            Integer configuredPort = environment == null ? null : environment.getProperty("server.port", Integer.class);
            if (configuredPort != null && configuredPort > 0) {
                return configuredPort;
            }
        } catch (Exception e) {
            log.debug("{}读取 server.port 失败: {}", LOG_PREFIX, e.getMessage());
        }
        log.warn("{}无法探测监听端口，将回退为 {}", LOG_PREFIX, FALLBACK_PORT);
        return FALLBACK_PORT;
    }

    /**
     * 判断字符串非 null 且包含非空白字符。
     *
     * @param value 待判断字符串
     * @return 非空白时返回 true
     */
    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /**
     * 异常简要描述：优先使用异常消息，消息为空时回退异常类型名。
     *
     * @param e 异常
     * @return 简要描述
     */
    private static String describe(Throwable e) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
    }
}
