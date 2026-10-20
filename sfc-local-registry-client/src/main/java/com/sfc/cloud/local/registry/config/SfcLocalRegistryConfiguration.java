package com.sfc.cloud.local.registry.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sfc.cloud.local.registry.SfcLocalAutoServiceRegistration;
import com.sfc.cloud.local.registry.SfcLocalDiscoveryClient;
import com.sfc.cloud.local.registry.SfcLocalRegistration;
import com.sfc.cloud.local.registry.SfcLocalRegistryHttpClient;
import com.sfc.cloud.local.registry.SfcLocalServiceRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.client.serviceregistry.AutoServiceRegistrationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * local 注册中心客户端的 Bean 装配。
 * <p>
 * 由 {@link SfcLocalRegistryAutoConfiguration} 在客户端启用（配置了
 * {@code spring.cloud.sfc-local.server-addr} 且未被禁用）时通过 {@code @Import} 引入，
 * 因此本类不再重复声明启用条件，只负责装配 HTTP 客户端、注册信息、注册/发现实现与自动注册生命周期。
 */
@Configuration(proxyBeanMethods = false)
public class SfcLocalRegistryConfiguration {
    /**
     * 默认服务名。
     */
    private static final String DEFAULT_SERVICE_NAME = "application";

    /**
     * 创建注册中心 HTTP 客户端。
     *
     * @param properties           客户端配置
     * @param objectMapperProvider JSON 序列化器提供者
     * @return HTTP 客户端
     */
    @Bean
    @ConditionalOnMissingBean
    public SfcLocalRegistryHttpClient sfcLocalRegistryHttpClient(
            SfcLocalRegistryProperties properties,
            ObjectProvider<ObjectMapper> objectMapperProvider) {
        return new SfcLocalRegistryHttpClient(properties, objectMapperProvider.getIfAvailable());
    }

    /**
     * 创建当前实例注册信息，端口在 Web 容器绑定后回填。
     *
     * @param properties  客户端配置
     * @param environment Spring 环境
     * @return 当前实例注册信息
     */
    @Bean
    @ConditionalOnMissingBean
    public SfcLocalRegistration sfcLocalRegistration(SfcLocalRegistryProperties properties,
                                                     Environment environment) {
        String serviceName = hasText(properties.getServiceName())
                ? properties.getServiceName().trim()
                : environment.getProperty("spring.application.name", DEFAULT_SERVICE_NAME);
        String host = hasText(properties.getHost()) ? properties.getHost().trim() : resolveHost();
        return new SfcLocalRegistration(serviceName, host, -1, properties.isSecure(),
                properties.getMetadata(), properties.getInstanceId());
    }

    /**
     * 创建客户端注册实现。
     *
     * @param properties 客户端配置
     * @param httpClient HTTP 客户端
     * @return 客户端注册实现
     */
    @Bean
    @ConditionalOnMissingBean
    public SfcLocalServiceRegistry sfcLocalServiceRegistry(SfcLocalRegistryProperties properties,
                                                           SfcLocalRegistryHttpClient httpClient) {
        return new SfcLocalServiceRegistry(httpClient, properties);
    }

    /**
     * 创建客户端发现实现。消费方已有其他 DiscoveryClient 时不覆盖。
     *
     * @param httpClient HTTP 客户端
     * @return 客户端发现实现
     */
    @Bean
    @ConditionalOnMissingBean(DiscoveryClient.class)
    public SfcLocalDiscoveryClient sfcLocalDiscoveryClient(SfcLocalRegistryHttpClient httpClient) {
        return new SfcLocalDiscoveryClient(httpClient);
    }

    /**
     * 创建自动注册生命周期。
     *
     * @param applicationContext 应用上下文
     * @param serviceRegistry    客户端注册实现
     * @param properties         自动注册配置
     * @param registration       当前实例注册信息
     * @return 自动注册生命周期
     */
    @Bean
    @ConditionalOnWebApplication
    @ConditionalOnClass(name = "org.springframework.boot.web.server.context.WebServerInitializedEvent")
    @ConditionalOnProperty(prefix = "spring.cloud.service-registry.auto-registration",
            name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean(SfcLocalAutoServiceRegistration.class)
    public SfcLocalAutoServiceRegistration sfcLocalAutoServiceRegistration(
            ApplicationContext applicationContext,
            SfcLocalServiceRegistry serviceRegistry,
            AutoServiceRegistrationProperties properties,
            SfcLocalRegistration registration) {
        return new SfcLocalAutoServiceRegistration(applicationContext, serviceRegistry, properties, registration);
    }

    /**
     * 解析本机注册 host。
     *
     * @return 本机 IP，解析失败时回退 127.0.0.1
     */
    private static String resolveHost() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            return "127.0.0.1";
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
