package com.xiaotao.saltedfishcloud.service.registry.local;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.serviceregistry.Registration;

import java.net.URI;
import java.util.Map;

/**
 * 进程内注册的实例描述，实现 Spring Cloud 官方 {@link Registration}。
 * <p>
 * 端口在 Web 容器完成绑定后由 {@link LocalAutoServiceRegistration} 回填，
 * 因此 {@link #setPort(int)} 是唯一的可变入口。
 */
public class LocalRegistration implements Registration {
    /** 服务名，取自 spring.application.name */
    private final String serviceId;

    /** 本机对外主机名 */
    private final String host;

    /** 是否使用 HTTPS */
    private final boolean secure;

    /** 实例元数据 */
    private final Map<String, String> metadata;

    /** Web 容器实际绑定端口，注册前回填 */
    private volatile int port;

    /**
     * 创建实例注册信息。
     *
     * @param serviceId 服务名
     * @param host      主机名
     * @param port      端口，未绑定时为 -1
     * @param secure    是否 HTTPS
     * @param metadata  元数据
     */
    public LocalRegistration(String serviceId, String host, int port, boolean secure, Map<String, String> metadata) {
        this.serviceId = serviceId;
        this.host = host;
        this.port = port;
        this.secure = secure;
        this.metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
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
     * 获取主机名。
     *
     * @return 主机名
     */
    @Override
    public String getHost() {
        return host;
    }

    /**
     * 获取端口。
     *
     * @return 端口
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
     * 获取实例 ID，格式为 {@code host:port}。
     *
     * @return 实例 ID
     */
    @Override
    public String getInstanceId() {
        return host + ":" + port;
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
        return "LocalRegistration{serviceId='" + serviceId + "', host='" + host + "', port=" + port + '}';
    }
}
