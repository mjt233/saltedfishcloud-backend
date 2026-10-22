package com.sfc.cloud.task.worker;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.UUID;

/**
 * 当前 worker 实例的身份信息（服务名、worker 标识、回调地址）。
 *
 * <p>这些值可能与 Web 容器的实际监听端口有关（{@code server.port=0} 时端口随机），因此全部惰性解析；
 * Worker 在 {@code SmartLifecycle#start()} 阶段才开始使用（此时 Web 容器已完成端口绑定）。</p>
 */
@Slf4j
class SfcTaskWorkerIdentity {

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
     * worker 标识中随机后缀的长度
     */
    private static final int RANDOM_SUFFIX_LENGTH = 8;

    /**
     * 配置项
     */
    private final SfcTaskWorkerProperties properties;

    /**
     * Spring 环境
     */
    private final Environment environment;

    /**
     * Spring 应用上下文
     */
    private final ApplicationContext applicationContext;

    /**
     * 惰性解析并缓存的服务名
     */
    private volatile String serviceName;

    /**
     * 惰性解析并缓存的 worker 标识
     */
    private volatile String workerId;

    /**
     * 惰性解析并缓存的本机地址
     */
    private volatile String host;

    /**
     * 惰性解析并缓存的端口
     */
    private volatile int port = -1;

    /**
     * 构造身份信息（由自动装配创建）。
     *
     * @param properties         配置项
     * @param environment        Spring 环境
     * @param applicationContext Spring 应用上下文
     */
    SfcTaskWorkerIdentity(SfcTaskWorkerProperties properties, Environment environment,
                          ApplicationContext applicationContext) {
        this.properties = properties;
        this.environment = environment;
        this.applicationContext = applicationContext;
    }

    /**
     * 获取服务名：优先配置项，其次 {@code spring.application.name}，最后兜底值。
     *
     * @return 服务名
     */
    synchronized String getServiceName() {
        if (serviceName == null) {
            String configured = properties.getServiceName();
            if (hasText(configured)) {
                serviceName = configured.trim();
            } else {
                String applicationName = environment.getProperty("spring.application.name");
                serviceName = hasText(applicationName) ? applicationName.trim() : DEFAULT_SERVICE_NAME;
            }
        }
        return serviceName;
    }

    /**
     * 获取 worker 唯一标识。
     *
     * <p>配置了 {@code sfc.task.worker.worker-id} 时直接使用；否则自动生成
     * {@code 服务名:主机:端口:随机后缀}。</p>
     *
     * @return worker 标识
     */
    synchronized String getWorkerId() {
        if (workerId == null) {
            String configured = properties.getWorkerId();
            if (hasText(configured)) {
                workerId = configured.trim();
            } else {
                String suffix = UUID.randomUUID().toString().replace("-", "")
                        .substring(0, RANDOM_SUFFIX_LENGTH);
                workerId = getServiceName() + ":" + getHost() + ":" + getPort() + ":" + suffix;
            }
        }
        return workerId;
    }

    /**
     * 获取 core 回调本实例的基础地址。
     *
     * <p>配置了 {@code sfc.task.worker.callback-base-url} 时直接使用；否则自动探测为
     * {@code http(s)://主机:端口}。</p>
     *
     * @return 回调基础地址（不含路径、不含末尾斜杠）
     */
    synchronized String getCallbackBaseUrl() {
        String configured = properties.getCallbackBaseUrl();
        if (hasText(configured)) {
            String normalized = configured.trim();
            while (normalized.endsWith("/")) {
                normalized = normalized.substring(0, normalized.length() - 1);
            }
            return normalized;
        }
        return (isSecure() ? "https" : "http") + "://" + getHost() + ":" + getPort();
    }

    /**
     * 获取本机地址：优先 {@code server.address}（非通配地址时），否则解析本机 IP。
     *
     * @return 主机地址
     */
    synchronized String getHost() {
        if (host == null) {
            String configured = environment.getProperty("server.address");
            if (hasText(configured) && !"0.0.0.0".equals(configured.trim()) && !"::".equals(configured.trim())) {
                host = configured.trim();
            } else {
                try {
                    host = InetAddress.getLocalHost().getHostAddress();
                } catch (UnknownHostException e) {
                    log.warn("{}无法解析本机地址，回调地址将回退为 {}: {}", LOG_PREFIX, FALLBACK_HOST, e.getMessage());
                    host = FALLBACK_HOST;
                }
            }
        }
        return host;
    }

    /**
     * 获取 Web 容器实际监听端口：优先 Boot 写入的 {@code local.server.port}（随机端口场景也准确），
     * 其次 {@code server.port}，最后兜底 8080。
     *
     * @return 端口
     */
    synchronized int getPort() {
        if (port <= 0) {
            port = resolvePort();
        }
        return port;
    }

    /**
     * 解析监听端口。
     *
     * @return 端口
     */
    private int resolvePort() {
        try {
            Integer localPort = environment.getProperty("local.server.port", Integer.class);
            if (localPort != null && localPort > 0) {
                return localPort;
            }
        } catch (Exception e) {
            log.debug("{}读取 local.server.port 失败: {}", LOG_PREFIX, e.getMessage());
        }
        try {
            Integer configuredPort = environment.getProperty("server.port", Integer.class);
            if (configuredPort != null && configuredPort > 0) {
                return configuredPort;
            }
        } catch (Exception e) {
            log.debug("{}读取 server.port 失败: {}", LOG_PREFIX, e.getMessage());
        }
        if (applicationContext == null) {
            log.warn("{}无法从应用上下文探测端口，回调地址将回退为 {}", LOG_PREFIX, FALLBACK_PORT);
        }
        return FALLBACK_PORT;
    }

    /**
     * 判断本实例是否对外提供 HTTPS（决定自动探测的回调地址协议）。
     *
     * @return 启用 SSL 返回 true
     */
    private boolean isSecure() {
        try {
            return Boolean.TRUE.equals(environment.getProperty("server.ssl.enabled", Boolean.class));
        } catch (Exception e) {
            return false;
        }
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
}
