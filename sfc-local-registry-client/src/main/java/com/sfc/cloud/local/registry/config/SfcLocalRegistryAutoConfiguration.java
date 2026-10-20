package com.sfc.cloud.local.registry.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.client.serviceregistry.ServiceRegistry;
import org.springframework.context.annotation.Import;

/**
 * local 注册中心客户端自动配置。
 * <p>
 * 仅在配置了 {@code spring.cloud.sfc-local.server-addr} 且未禁用时生效，因此宿主自身
 * （使用进程内注册表）不会创建客户端 Bean。
 * <p>
 * 本类只负责声明启用条件，具体 Bean 装配委托给同包的 {@link SfcLocalRegistryConfiguration}
 * 与 {@link SfcLocalReactiveDiscoveryConfiguration}。
 */
@AutoConfiguration
@ConditionalOnClass({DiscoveryClient.class, ServiceRegistry.class})
@ConditionalOnProperty(prefix = "spring.cloud.sfc-local", name = "enabled",
        havingValue = "true", matchIfMissing = true)
@ConditionalOnProperty(prefix = "spring.cloud.sfc-local", name = "server-addr")
@EnableConfigurationProperties(SfcLocalRegistryProperties.class)
@Import({SfcLocalRegistryConfiguration.class, SfcLocalReactiveDiscoveryConfiguration.class})
public class SfcLocalRegistryAutoConfiguration {
}
