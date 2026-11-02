package com.xiaotao.saltedfishcloud.service.registry.local;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.serviceregistry.AutoServiceRegistrationProperties;
import org.springframework.cloud.commons.util.InetUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.util.Map;

/**
 * local 注册中心服务端装配。
 * <p>
 * 仅在 {@code spring.cloud.sfc-local.registry.enabled=true}（默认）时生效；Nacos 注册中心由
 * Spring Cloud Alibaba 的自动配置提供，两者应二选一（同时启用会因存在两个 {@code Registration}
 * Bean 导致启动失败）。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = SfcLocalRegistryServerProperties.PREFIX, name = "enabled",
        havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(SfcLocalRegistryServerProperties.class)
public class LocalRegistryConfiguration {
    /**
     * 进程内注册表。关闭动作由 {@link LocalAutoServiceRegistration} 的生命周期统一触发。
     *
     * @param properties 本地注册中心服务端配置
     * @return 进程内注册表
     */
    @Bean(destroyMethod = "")
    public LocalServiceRegistry localServiceRegistry(SfcLocalRegistryServerProperties properties) {
        return new LocalServiceRegistry(properties);
    }

    /**
     * 外部注册接口的密码校验服务。
     *
     * @param properties 本地注册中心服务端配置
     * @return 密码校验服务
     */
    @Bean
    public LocalRegistryAuthService localRegistryAuthService(SfcLocalRegistryServerProperties properties) {
        return new LocalRegistryAuthService(properties);
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
