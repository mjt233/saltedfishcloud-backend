package com.sfc.cloud.auth;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

import java.util.Optional;

/**
 * 内部接口鉴权 SDK 的自动装配。
 *
 * <p>只装配"两类角色都需要"的东西：配置项、共享密钥、签发器、校验器，以及<b>服务端</b>的校验过滤器。
 * <b>刻意不装配客户端拦截器</b>——凭证只应发往 core 的内部接口，若注册成全局 Feign 拦截器，
 * 它会跟着所有服务的调用一起发出去，扩大密钥签发能力的暴露面。客户端适配器由消费方显式挂载
 * （见 {@code com.sfc.cloud.auth.client}）。</p>
 *
 * <p>本模块被 core（Servlet）与 microservices（网关为 WebFlux）同时依赖，因此服务端部分用
 * {@link ConditionalOnWebApplication} + {@link ConditionalOnClass} 双重条件隔离，
 * 保证 WebFlux 进程里不会尝试加载 Servlet 类。</p>
 */
@Slf4j
@AutoConfiguration
@EnableConfigurationProperties(SfcInternalAuthProperties.class)
public class SfcInternalAuthAutoConfiguration {

    /**
     * 构造共享密钥：集中在这里做"未配置"与"过弱"两种判定，保证全进程只打一条结论性日志。
     *
     * @param properties  配置项
     * @param environment 环境对象，用于兜底解析服务名（仅日志用途）
     * @return 共享密钥状态（未配置时为 fail-closed 状态）
     * @throws IllegalStateException 密钥非空但长度不足时抛出，直接让服务启动失败
     */
    @Bean
    public SfcInternalAuthKey sfcInternalAuthKey(SfcInternalAuthProperties properties, Environment environment) {
        SfcInternalAuthKey authKey = SfcInternalAuthKey.fromKeyText(
                Optional.ofNullable(properties.getKey())
                        .filter(t -> !t.isEmpty())
                        .orElseGet(() -> System.getenv(SfcInternalAuthConstants.Env.INTERNAL_KEY))
        );
        if (!authKey.isConfigured()) {
            log.error("[内部接口] 未配置 sfc.internal.key（环境变量 SFC_INTERNAL_KEY）："
                    + "本服务将拒绝全部 {}** 请求（fail-closed），调用其他服务内部接口时也无法生成凭证。"
                    + "微服务模式下必须为 core 与所有微服务配置同一份密钥", SfcInternalAuthConstants.PATH_PREFIX);
        } else {
            log.info("[内部接口] 鉴权已启用：凭证头 {}，路径前缀 {}，服务名 {}",
                    SfcInternalAuthConstants.Header.INTERNAL_TOKEN, SfcInternalAuthConstants.PATH_PREFIX,
                    resolveServiceName(properties, environment));
        }
        return authKey;
    }

    /**
     * 凭证签发器（客户端角色使用）。
     *
     * @param authKey     共享密钥
     * @param properties  配置项
     * @param environment 环境对象，用于兜底解析服务名
     * @return 签发器
     */
    @Bean
    @ConditionalOnMissingBean
    public SfcInternalTokenIssuer sfcInternalTokenIssuer(SfcInternalAuthKey authKey,
                                                         SfcInternalAuthProperties properties,
                                                         Environment environment) {
        return new SfcInternalTokenIssuer(authKey, resolveServiceName(properties, environment),
                properties.getTokenTtl());
    }

    /**
     * 凭证校验器（服务端角色使用）。
     *
     * @param authKey    共享密钥
     * @param properties 配置项
     * @return 校验器
     */
    @Bean
    @ConditionalOnMissingBean
    public SfcInternalTokenVerifier sfcInternalTokenVerifier(SfcInternalAuthKey authKey,
                                                             SfcInternalAuthProperties properties) {
        return new SfcInternalTokenVerifier(authKey, properties.getClockSkew(), properties.getAllowedIssuers());
    }

    /**
     * 解析本服务名：优先配置项，其次 {@code spring.application.name}，最后兜底值。
     *
     * @param properties  配置项
     * @param environment 环境对象
     * @return 服务名
     */
    private static String resolveServiceName(SfcInternalAuthProperties properties, Environment environment) {
        if (StringUtils.hasText(properties.getServiceName())) {
            return properties.getServiceName();
        }
        String applicationName = environment == null ? null : environment.getProperty("spring.application.name");
        return StringUtils.hasText(applicationName)
                ? applicationName
                : SfcInternalAuthConstants.DEFAULT_SERVICE_NAME;
    }

    /**
     * 服务端装配：Servlet 应用中的内部接口校验过滤器。
     *
     * <p>默认开启（{@code sfc.internal.server-enabled=false} 可关闭）</p>
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnClass(name = "jakarta.servlet.Filter")
    public static class ServerSideConfiguration {

        /**
         * 内部接口校验过滤器。
         *
         * @param verifier 凭证校验器
         * @return 过滤器
         */
        @Bean
        @ConditionalOnMissingBean
        public SfcInternalAuthFilter sfcInternalAuthFilter(SfcInternalTokenVerifier verifier) {
            return new SfcInternalAuthFilter(verifier);
        }
    }
}
