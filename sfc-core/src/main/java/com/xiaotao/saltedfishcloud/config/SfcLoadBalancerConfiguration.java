package com.xiaotao.saltedfishcloud.config;

import com.sfc.cloud.auth.SfcInternalAuthKey;
import com.sfc.cloud.auth.SfcInternalTokenIssuer;
import com.sfc.cloud.auth.client.SfcInternalTokenRequestInitializer;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * 宿主调用其他微服务的 HTTP 客户端配置。
 * <p>
 * 通过 Spring Cloud LoadBalancer 官方机制，把以服务名书写的目标地址（{@code http://服务名/路径}）
 * 在请求发出前改写为负载均衡选出的真实实例地址，调用方无需再从
 * {@link org.springframework.cloud.client.ServiceInstance} 中读取 host/port 自行拼 URL。
 * <p>
 * 对应的协议实现由 {@code spring-cloud-starter-loadbalancer} 提供，实例来源为当前生效的
 * {@link org.springframework.cloud.client.discovery.DiscoveryClient}（local 进程内注册表或 Nacos）。
 */
@Configuration(proxyBeanMethods = false)
public class SfcLoadBalancerConfiguration {

    /**
     * 负载均衡 RestClient.Builder。
     * <p>
     * {@link LoadBalanced @LoadBalanced} 必须标注在 {@code @Bean} 方法上：Spring Cloud 的
     * {@code LoadBalancerRestClientBuilderBeanPostProcessor} 会为该方法返回的 builder 追加
     * 负载均衡请求拦截器，使其构建出的 {@link RestClient} 把请求 URI 的 host 当作服务名，
     * 按服务名选择真实实例并重写 scheme / host / port。
     *
     * @return 负载均衡 RestClient.Builder
     */
    @Bean
    @LoadBalanced
    public RestClient.Builder sfcLoadBalancedRestClientBuilder() {
        return RestClient.builder();
    }

    /**
     * 微服务调用 RestClient。
     * <p>
     * 服务间内部接口鉴权：已配置共享密钥（{@code sfc.internal.key}）时，为每次出站请求签发内部调用凭证
     * （{@link SfcInternalTokenRequestInitializer}）；未配置密钥时跳过凭证头，
     * 保证「本地模式 / 未启用内部鉴权」场景下对公开端点的调用仍然可用。
     * <p>
     * 注意：这里刻意不标注 {@code @LoadBalanced}——负载均衡能力来自已被后处理的 builder，
     * {@link RestClient} 本身无需再参与 {@code @LoadBalanced} 的 Bean 识别。
     *
     * @param builder 负载均衡 RestClient.Builder（由 {@link #sfcLoadBalancedRestClientBuilder()} 提供）
     * @param issuer  内部调用凭证签发器（由 sfc-auth SDK 自动装配）
     * @param authKey 内部接口共享密钥，用于判断是否已配置密钥
     * @return 带负载均衡与可选内部调用凭证的 RestClient
     */
    @Bean
    public RestClient sfcServiceRestClient(RestClient.Builder builder,
                                           SfcInternalTokenIssuer issuer,
                                           SfcInternalAuthKey authKey) {
        return builder
                .requestInitializers(initializers -> {
                    if (authKey.isConfigured()) {
                        initializers.add(new SfcInternalTokenRequestInitializer(issuer));
                    }
                })
                .build();
    }
}
