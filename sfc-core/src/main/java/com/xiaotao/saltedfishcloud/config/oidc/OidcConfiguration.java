package com.xiaotao.saltedfishcloud.config.oidc;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * OIDC 服务端配置类。
 * <p>
 * 以窄作用域方式注册 {@link OidcServerProperty}，避免在主应用类上使用全局
 * {@code @ConfigurationPropertiesScan} 带来的副作用。
 * </p>
 * <p>
 * 在 Spring 上下文初始化完成后，通过 {@link #validateOidcProperty()} 对配置进行快速失败校验，
 * 确保 {@code sys.oidc.enabled=true} 时 {@code issuer} 不为空。
 * </p>
 */
@Configuration
@EnableConfigurationProperties(OidcServerProperty.class)
@RequiredArgsConstructor
public class OidcConfiguration {

    private final OidcServerProperty oidcServerProperty;

    /**
     * 在 Bean 初始化后执行配置校验。
     * <p>
     * 当 {@code sys.oidc.enabled=true} 且 {@code issuer} 为空时，抛出
     * {@link IllegalStateException} 使应用快速失败，避免以不合法的配置运行。
     * </p>
     *
     * @throws IllegalStateException 当 OIDC 已启用但 issuer 为空时抛出
     * @see OidcServerProperty#validate()
     */
    @PostConstruct
    public void validateOidcProperty() {
        oidcServerProperty.validate();
    }
}
