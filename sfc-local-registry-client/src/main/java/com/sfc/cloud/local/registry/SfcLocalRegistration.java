package com.sfc.cloud.local.registry;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.serviceregistry.Registration;

import java.net.URI;
import java.util.Map;

/**
 * local 注册中心的实例描述，实现 Spring Cloud 官方 {@link Registration}。
 * <p>
 * 端口由 {@link SfcLocalAutoServiceRegistration} 在 Web 容器绑定后回填，
 * 因此 {@link #setPort(int)} 是唯一的可变入口。
 */
public class SfcLocalRegistration implements Registration {
    /** 服务名 */
    private final String serviceId;

    /** 注册 host */
    private final String host;

    /** 是否 HTTPS */
    private final boolean secure;

    /** 实例元数据 */
    private final Map<String, String> metadata;

    /** 自定义实例 ID，为空时使用 host:port */
    private final String configuredInstanceId;

    /** Web 容器实际绑定端口，注册前回填 */
    private volatile int port;

    /**
     * 创建实例注册信息。
     *
     * @param serviceId           服务名
     * @param host                注册 host
     * @param port                端口，未绑定时为 -1
     * @param secure              是否 HTTPS
     * @param metadata            实例元数据
     * @param configuredInstanceId 自定义实例 ID，可为空
     */
    public SfcLocalRegistration(String serviceId,
                                String host,
                                int port,
                                boolean secure,
                                Map<String, String> metadata,
                                String configuredInstanceId) {
        this.serviceId = serviceId;
        this.host = host;
        this.port = port;
        this.secure = secure;
        this.metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        this.configuredInstanceId = configuredInstanceId;
    }

    /**
     * 获取服务名。
     *
     * @return 服务名
     */
    @Override
    public String getServiceId() {
        return serviceId;
    }

    /**
     * 获取注册 host。
     *
     * @return 注册 host
     */
    @Override
    public String getHost() {
        return host;
    }

    /**
     * 获取端口。
     *
     * @return 端口，未绑定时为 -1
     */
    @Override
    public int getPort() {
        return port;
    }

    /**
     * 是否使用 HTTPS。
     *
     * @return HTTPS 时返回 true
     */
    @Override
    public boolean isSecure() {
        return secure;
    }

    /**
     * 获取实例 URI。
     *
     * @return 实例 URI
     */
    @Override
    public URI getUri() {
        return ServiceInstance.createUri(this);
    }

    /**
     * 获取实例元数据。
     *
     * @return 元数据，非 null
     */
    @Override
    public Map<String, String> getMetadata() {
        return metadata;
    }

    /**
     * 获取实例 ID。优先使用配置值，否则为 {@code host:port}。
     *
     * @return 实例 ID
     */
    @Override
    public String getInstanceId() {
        return configuredInstanceId == null || configuredInstanceId.isBlank()
                ? host + ":" + port
                : configuredInstanceId;
    }

    /**
     * 获取 URL scheme。
     *
     * @return {@code http} 或 {@code https}
     */
    @Override
    public String getScheme() {
        return secure ? "https" : "http";
    }

    /**
     * 回填 Web 容器实际绑定端口。
     *
     * @param port 实际端口
     */
    public void setPort(int port) {
        this.port = port;
    }

    @Override
    public String toString() {
        return "SfcLocalRegistration{serviceId='" + serviceId + "', host='" + host + "', port=" + port + '}';
    }
}
