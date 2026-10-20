package com.xiaotao.saltedfishcloud.service.registry.local;

import com.xiaotao.saltedfishcloud.config.SysProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.client.serviceregistry.AutoServiceRegistrationProperties;
import org.springframework.cloud.commons.util.InetUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.util.Map;

/**
 * local provider 装配。
 * <p>
 * 仅在 {@code sys.service.registry-provider=local}（默认）时生效；Nacos provider 由
 * Spring Cloud Alibaba 的自动配置提供，两者不会同时存在。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "sys.service", name = "registry-provider", havingValue = "local", matchIfMissing = true)
public class LocalRegistryConfiguration {
    /**
     * 进程内注册表。关闭动作由 {@link LocalAutoServiceRegistration} 的生命周期统一触发。
     *
     * @param sysProperties 系统配置
     * @return 进程内注册表
     */
    @Bean(destroyMethod = "")
    public LocalServiceRegistry localServiceRegistry(SysProperties sysProperties) {
        return new LocalServiceRegistry(sysProperties);
    }

    /**
     * 外部注册接口的密码校验服务。
     *
     * @param sysProperties 系统配置
     * @return 密码校验服务
     */
    @Bean
    public LocalRegistryAuthService localRegistryAuthService(SysProperties sysProperties) {
        return new LocalRegistryAuthService(sysProperties);
    }

    /**
     * 当前实例的注册信息。
     *
     * @param environment 运行环境，用于读取 {@code spring.application.name}
     * @param inetUtils   本机地址解析工具
     * @return 当前实例注册信息
     */
    @Bean
    public LocalRegistration localRegistration(Environment environment, InetUtils inetUtils) {
        String serviceId = environment.getProperty("spring.application.name", "application");
        String host = inetUtils.findFirstNonLoopbackHostInfo().getHostname();
        return new LocalRegistration(serviceId, host, -1, false, Map.of());
    }

    /**
     * 自动注册生命周期，仅在 Spring Cloud 自动注册开启时装配。
     *
     * @param applicationContext 应用上下文
     * @param localServiceRegistry 进程内注册表
     * @param properties         自动注册配置
     * @param registration       当前实例注册信息
     * @return 自动注册生命周期
     */
    @Bean
    @ConditionalOnProperty(prefix = "spring.cloud.service-registry.auto-registration",
            name = "enabled", havingValue = "true", matchIfMissing = true)
    public LocalAutoServiceRegistration localAutoServiceRegistration(ApplicationContext applicationContext,
                                                                     LocalServiceRegistry localServiceRegistry,
                                                                     AutoServiceRegistrationProperties properties,
                                                                     LocalRegistration registration) {
        return new LocalAutoServiceRegistration(applicationContext, localServiceRegistry, properties, registration);
    }
}
