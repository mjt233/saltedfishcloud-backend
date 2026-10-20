package com.sfc.cloud.local.registry.config;

import com.sfc.cloud.local.registry.SfcLocalReactiveDiscoveryClient;
import com.sfc.cloud.local.registry.SfcLocalRegistryHttpClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cloud.client.ConditionalOnReactiveDiscoveryEnabled;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * local 注册中心客户端的反应式发现 Bean 装配。
 * <p>
 * 由 {@link SfcLocalRegistryAutoConfiguration} 在客户端启用时通过 {@code @Import} 引入，本类只声明
 * 反应式发现的生效条件：classpath 具备 {@code WebClient} 且未关闭
 * {@code spring.cloud.discovery.reactive.enabled}。因此 Servlet 服务不会创建该 Bean，
 * 网关等 WebFlux 应用则获得可被 {@code ReactiveCompositeDiscoveryClient} 聚合的委托实现。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnReactiveDiscoveryEnabled
public class SfcLocalReactiveDiscoveryConfiguration {
    /**
     * 创建反应式客户端发现实现。消费方自定义同类型 Bean 时不覆盖。
     *
     * @param httpClient HTTP 客户端
     * @return 反应式客户端发现实现
     */
    @Bean
    @ConditionalOnMissingBean(SfcLocalReactiveDiscoveryClient.class)
    public SfcLocalReactiveDiscoveryClient sfcLocalReactiveDiscoveryClient(
            SfcLocalRegistryHttpClient httpClient) {
        return new SfcLocalReactiveDiscoveryClient(httpClient);
    }
}
