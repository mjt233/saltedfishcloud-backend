package com.sfc.cloud.remote.client;

import com.sfc.cloud.auth.SfcInternalTokenIssuer;
import com.sfc.cloud.auth.client.SfcInternalTokenFeignInterceptor;
import com.sfc.cloud.auth.client.SfcInternalTokenRequestInitializer;
import com.sfc.cloud.remote.client.support.*;
import com.sfc.cloud.constants.SfcServiceName;
import feign.Client;
import feign.RequestInterceptor;
import feign.codec.ErrorDecoder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

/**
 * 远程客户端自动装配。
 *
 * <p>两条链路各自要带两类凭证，互不混淆：</p>
 * <ul>
 *   <li><b>服务身份</b>：{@link SfcInternalTokenFeignInterceptor} / {@link SfcInternalTokenRequestInitializer}
 *       ——每次出站请求新签一个内部调用凭证（HS256 JWT），与入站上下文无关；</li>
 *   <li><b>用户凭证</b>：{@link FeignCredentialForwardingInterceptor} / {@link SfcCredentialRequestInitializer}
 *       ——把入站请求的用户凭证透传给 core，仅在存在入站请求时生效。</li>
 * </ul>
 *
 * <p>签发器与校验器由 {@code sfc-auth} SDK 的自动装配提供（配置项前缀 {@code sfc.internal}）。</p>
 */
@AutoConfiguration
@ConditionalOnClass(Client.class)
@EnableFeignClients(clients = {SfcCoreFileFeignClient.class, SfcCoreUserFeignClient.class, SfcCoreConfigFeignClient.class})
@EnableConfigurationProperties(SfcRemoteClientProperties.class)
public class SfcRemoteClientAutoConfiguration {

    /**
     * 凭证透传拦截器（Feign）
     *
     * @return Feign 请求拦截器
     */
    @Bean
    @ConditionalOnMissingBean(name = "sfcCredentialForwardingInterceptor")
    public RequestInterceptor sfcCredentialForwardingInterceptor() {
        return new FeignCredentialForwardingInterceptor();
    }

    /**
     * 服务间调用凭证拦截器（Feign）：为每次调用签发内部调用凭证。
     * 若未启用服务间内部认证机制，则不注册这个Bean
     *
     * @param issuer 凭证签发器（由 sfc-auth SDK 提供）
     * @return Feign 请求拦截器
     */
    @Bean
    @ConditionalOnMissingBean(name = "sfcInternalTokenInterceptor")
    @ConditionalOnBean(SfcInternalTokenIssuer.class)
    public RequestInterceptor sfcInternalTokenInterceptor(SfcInternalTokenIssuer issuer) {
        return new SfcInternalTokenFeignInterceptor(issuer);
    }

    /** core 错误信封解码器（Feign） */
    @Bean
    @ConditionalOnMissingBean
    public ErrorDecoder sfcRemoteErrorDecoder() {
        return new SfcRemoteErrorDecoder();
    }

    /**
     * sfc-core 网盘文件操作统一客户端（对外唯一调用入口）。
     *
     * @param feignClient        Feign 链路实现：列表 / 搜索 / 下载 / 创建目录 / 重命名 / 复制 / 移动 / 删除
     * @param properties         客户端配置项
     * @param loadBalancerClient 服务发现选路客户端
     * @param issuer             内部调用凭证签发器
     * @return 统一入口客户端
     */
    @Bean
    @ConditionalOnMissingBean
    public SfcCoreFileClient sfcCoreFileClient(SfcCoreFileFeignClient feignClient,
                                               SfcRemoteClientProperties properties,
                                               LoadBalancerClient loadBalancerClient,
                                               @Autowired(required = false) SfcInternalTokenIssuer issuer
    ) {
        return new SfcCoreFileClient(feignClient, buildStreamFileClient(properties, loadBalancerClient, issuer));
    }

    /**
     * sfc-core 用户身份查询统一客户端（获取当前用户与管理员判定）。
     *
     * @param feignClient Feign 链路实现：获取当前登录用户
     * @return 用户身份查询客户端
     */
    @Bean
    @ConditionalOnMissingBean
    public SfcCoreUserClient sfcCoreUserClient(SfcCoreUserFeignClient feignClient) {
        return new SfcCoreUserClient(feignClient);
    }

    /**
     * 构建流式上传客户端（包内实现，不注册为 bean）。
     *
     * @param properties         客户端配置项
     * @param loadBalancerClient 服务发现选路客户端
     * @param issuer             内部调用凭证签发器
     * @return 流式上传客户端
     */
    private SfcCoreStreamFileClient buildStreamFileClient(SfcRemoteClientProperties properties,
                                                          LoadBalancerClient loadBalancerClient,
                                                          SfcInternalTokenIssuer issuer) {
        HttpClient.Builder httpClientBuilder = HttpClient.newBuilder();
        if (properties.getConnectTimeout() != null) {
            httpClientBuilder.connectTimeout(properties.getConnectTimeout());
        }
        JdkClientHttpRequestFactory jdkRequestFactory = new JdkClientHttpRequestFactory(httpClientBuilder.build());
        if (properties.getReadTimeout() != null) {
            jdkRequestFactory.setReadTimeout(properties.getReadTimeout());
        }
        ClientHttpRequestFactory requestFactory = new SfcLoadBalancedClientHttpRequestFactory(
                jdkRequestFactory, loadBalancerClient);
        RestClient restClient = RestClient.builder()
                .baseUrl("lb://" + SfcServiceName.CORE)
                .requestFactory(requestFactory)
                // 服务身份（每请求签发内部调用凭证，与入站上下文无关）＋ 用户凭证透传（有入站上下文时）
                .requestInitializers(initializers -> {
                    initializers.add(new SfcInternalTokenRequestInitializer(issuer));
                    initializers.add(new SfcCredentialRequestInitializer());
                })
                .defaultStatusHandler(new SfcRemoteResponseErrorHandler())
                .build();

        return new SfcCoreStreamFileClient(restClient);
    }
}
