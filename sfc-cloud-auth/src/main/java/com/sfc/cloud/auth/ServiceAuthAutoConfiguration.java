package com.sfc.cloud.auth;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 微服务侧委托令牌验签的自动配置入口。
 * <p>
 * 经 {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports} 注册，
 * 因此微服务侧<b>只需加一个依赖 ＋ 一段配置</b>，不必写任何装配代码。
 * </p>
 *
 * <h3>装配了哪些部件</h3>
 * <table border="1">
 *     <caption>Bean 清单</caption>
 *     <tr><th>Bean</th><th>作用</th></tr>
 *     <tr><td>{@link JwksProvider}</td><td>拉取 / 缓存 core 的 JWKS 公钥，未知 kid 回源一次</td></tr>
 *     <tr><td>{@link InternalTokenVerifier}</td><td>RS256 离线验签，强制校验 iss / aud == 自己 / exp，只读 sub</td></tr>
 *     <tr><td>{@link InternalTokenAuthFilter}</td><td>fail-closed 过滤器，作为普通 {@code Filter} Bean 被 Spring Boot 注册到 {@code /*}</td></tr>
 *     <tr><td>{@link RequireInternalTokenInterceptor}</td><td>端点级作用域校验，不足返回 403</td></tr>
 * </table>
 *
 * <h3>配置校验</h3>
 * <p>
 * {@link ServiceAuthClientProperties} 是 {@code InitializingBean}，绑定完成后立即校验；
 * {@code enabled=true} 却缺少 {@code jwks-url} / {@code service-name} 时<b>启动直接失败</b>，
 * 而不是留一个"能启动但一律 401"或更糟"一律放行"的状态。
 * </p>
 */
@Configuration
@EnableConfigurationProperties(ServiceAuthClientProperties.class)
public class ServiceAuthAutoConfiguration {

    /**
     * 公钥提供者。
     *
     * @param properties 微服务侧验签配置
     * @return JWKS 公钥提供者
     */
    @Bean
    public JwksProvider jwksProvider(ServiceAuthClientProperties properties) {
        return new JwksProvider(properties);
    }

    /**
     * 委托令牌验签器。
     *
     * @param properties   微服务侧验签配置
     * @param jwksProvider 公钥提供者
     * @return 验签器
     */
    @Bean
    public InternalTokenVerifier internalTokenVerifier(ServiceAuthClientProperties properties,
                                                       JwksProvider jwksProvider) {
        return new InternalTokenVerifier(properties, jwksProvider);
    }

    /**
     * fail-closed 认证过滤器。
     * <p>
     * 以普通 {@code Filter} Bean 的形式暴露：Spring Boot 会把容器里的每个 {@code Filter} Bean
     * 自动注册到 {@code /*}，因此不需要额外的 {@code FilterRegistrationBean}，
     * 也避免了对 Boot 版本间 Web 模块拆分的依赖。
     * </p>
     *
     * @param properties 微服务侧验签配置
     * @param verifier   委托令牌验签器
     * @return 认证过滤器
     */
    @Bean
    public InternalTokenAuthFilter internalTokenAuthFilter(ServiceAuthClientProperties properties,
                                                           InternalTokenVerifier verifier) {
        return new InternalTokenAuthFilter(properties, verifier);
    }

    /**
     * 注册 {@link RequireInternalToken} 的作用域校验拦截器。
     *
     * @param properties 微服务侧验签配置
     * @return WebMvc 配置
     */
    @Bean
    public WebMvcConfigurer requireInternalTokenWebMvcConfigurer(ServiceAuthClientProperties properties) {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(new RequireInternalTokenInterceptor(properties))
                        .addPathPatterns("/**");
            }
        };
    }
}
