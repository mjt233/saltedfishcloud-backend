package com.xiaotao.saltedfishcloud.service.registry.local;

import org.springframework.cloud.client.serviceregistry.AbstractAutoServiceRegistration;
import org.springframework.cloud.client.serviceregistry.AutoServiceRegistrationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.util.Assert;

/**
 * local provider 的自动注册生命周期。
 * <p>
 * 复用 Spring Cloud 官方的 {@link AbstractAutoServiceRegistration}：Web 容器绑定端口后注册，
 * 容器关闭时注销并关闭注册表。
 */
public class LocalAutoServiceRegistration extends AbstractAutoServiceRegistration<LocalRegistration> {
    /** 当前实例注册信息 */
    private final LocalRegistration registration;

    /**
     * 创建自动注册生命周期。
     *
     * @param context        应用上下文
     * @param serviceRegistry 进程内注册表
     * @param properties     自动注册配置
     * @param registration   当前实例注册信息
     */
    public LocalAutoServiceRegistration(ApplicationContext context,
                                        LocalServiceRegistry serviceRegistry,
                                        AutoServiceRegistrationProperties properties,
                                        LocalRegistration registration) {
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
    protected LocalRegistration getRegistration() {
        if (registration.getPort() < 0 && getPort().get() > 0) {
            registration.setPort(getPort().get());
        }
        Assert.isTrue(registration.getPort() > 0, "service.port has not been set");
        return registration;
    }

    /**
     * 本 provider 不注册管理端口。
     *
     * @return 恒为 null
     */
    @Override
    protected LocalRegistration getManagementRegistration() {
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
     * @return 恒为 true
     */
    @Override
    protected boolean isEnabled() {
        return true;
    }
}
