package com.sfc.cloud.task.worker;

import org.springframework.cloud.client.serviceregistry.Registration;
import org.springframework.util.StringUtils;

/**
 * 当前 worker 实例在服务注册中心中的身份：{@code serviceId} 与实例地址 {@code host:port}。
 *
 * <p>身份完全取自 Spring Cloud 的 {@link Registration} bean</p>
 */
public class SfcTaskWorkerIdentity {

    /**
     * 当前实例的注册信息
     */
    private final Registration registration;

    /**
     * 构造身份信息（由自动装配创建）。
     *
     * @param registration 当前实例的注册信息
     */
    SfcTaskWorkerIdentity(Registration registration) {
        this.registration = registration;
    }

    /**
     * 获取服务名（服务注册中心中的 serviceId）。
     *
     * @return 服务名
     */
    public String getServiceId() {
        return registration.getServiceId();
    }

    /**
     * 获取实例地址（{@code host:port}）。
     *
     * @return 实例地址，形如 {@code 192.168.1.10:8080}
     */
    public String getWorkerId() {
        return getHost() + ":" + getPort();
    }

    /**
     * 获取实例主机地址（服务注册信息中的 host）。
     *
     * @return 主机地址
     */
    public String getHost() {
        return registration.getHost();
    }

    /**
     * 获取实例端口（服务注册信息中的 port）。
     *
     * @return 端口，Web 容器绑定端口前为 {@code -1}
     */
    public int getPort() {
        return registration.getPort();
    }

    /**
     * 判断服务注册信息是否已携带完整的实例地址（host + 端口）。
     *
     * <p>端口由服务注册实现在 Web 容器完成端口绑定后回填，未回填前实例地址还无法与注册中心中的实例比对。</p>
     *
     * @return 实例地址完整时返回 true
     */
    boolean hasRegisteredInstance() {
        return StringUtils.hasText(registration.getHost()) && registration.getPort() > 0;
    }
}
