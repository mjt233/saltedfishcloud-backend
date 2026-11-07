package com.sfc.cloud.local.registry;

import org.springframework.cloud.client.serviceregistry.AbstractAutoServiceRegistration;
import org.springframework.cloud.client.serviceregistry.AutoServiceRegistrationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.util.Assert;

/**
 * local 注册中心的自动注册生命周期。
 * <p>
 * 复用 Spring Cloud 官方 {@link AbstractAutoServiceRegistration}：Web 容器绑定端口后注册，
 * 容器关闭时注销。
 */
public class SfcLocalAutoServiceRegistration extends AbstractAutoServiceRegistration<SfcLocalRegistration> {
    /** 当前实例注册信息 */
    private final SfcLocalRegistration registration;

    /**
     * 创建自动注册生命周期。
     *
     * @param context         应用上下文
     * @param serviceRegistry 客户端注册实现
     * @param properties      自动注册配置
     * @param registration    当前实例注册信息
     */
    public SfcLocalAutoServiceRegistration(ApplicationContext context,
                                           SfcLocalServiceRegistry serviceRegistry,
                                           AutoServiceRegistrationProperties properties,
                                           SfcLocalRegistration registration) {
        super(context, serviceRegistry, properties);
        this.registration = registration;
    }

    /**
     * 获取当前实例注册信息，并在 Web 容器启动后回填真实端口。
     *
     * @return 当前实例注册信息
     */
    @Override
    @SuppressWarnings("deprecation")
    protected SfcLocalRegistration getRegistration() {
        if (registration.getPort() < 0 && getPort().get() > 0) {
            registration.setPort(getPort().get());
        }
        Assert.isTrue(registration.getPort() > 0, "service.port has not been set");
        return registration;
    }

    /**
     * 本客户端不注册管理端口。
     *
     * @return 恒为 null
     */
    @Override
    protected SfcLocalRegistration getManagementRegistration() {
        return null;
    }

    /**
     * 自动注册事件携带的配置对象。
     *
     * @return 当前实例注册信息
     */
    @Override
    protected Object getConfiguration() {
        return registration;
    }

    /**
     * 是否启用自动注册。
     *
     * @return 恒为 true，是否装配由配置条件决定
     */
    @Override
    protected boolean isEnabled() {
        return true;
    }
}
