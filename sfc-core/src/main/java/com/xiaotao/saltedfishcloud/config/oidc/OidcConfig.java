package com.xiaotao.saltedfishcloud.config.oidc;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * OIDC 配置类，负责注册 {@link OidcServerProperty} 配置属性 Bean。
 */
@Configuration
@EnableConfigurationProperties(OidcServerProperty.class)
public class OidcConfig {
}
