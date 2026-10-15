package com.sfc.cloud.remote.client;

import com.sfc.cloud.remote.client.support.CredentialForwardingInterceptor;
import com.sfc.cloud.remote.client.support.SfcCredentialRequestInitializer;
import com.sfc.cloud.remote.client.support.SfcLoadBalancedClientHttpRequestFactory;
import com.sfc.cloud.remote.client.support.SfcRemoteErrorDecoder;
import com.sfc.cloud.remote.client.support.SfcRemoteResponseErrorHandler;
import com.sfc.cloud.remote.constants.SfcServiceName;
import feign.Client;
import feign.RequestInterceptor;
import feign.codec.Encoder;
import feign.codec.ErrorDecoder;
import feign.form.spring.SpringFormEncoder;
import org.springframework.boot.autoconfigure.AutoConfiguration;
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
 * 远程客户端自动装配
 */
@AutoConfiguration
@ConditionalOnClass(Client.class)
@EnableFeignClients(clients = SfcCoreFileFeignClient.class)
@EnableConfigurationProperties(SfcRemoteClientProperties.class)
public class SfcRemoteClientAutoConfiguration {

    /** 凭证透传拦截器（Feign） */
    @Bean
    @ConditionalOnMissingBean
    public RequestInterceptor sfcCredentialForwardingInterceptor() {
        return new CredentialForwardingInterceptor();
    }

    /** core 错误信封解码器（Feign） */
    @Bean
    @ConditionalOnMissingBean
    public ErrorDecoder sfcRemoteErrorDecoder() {
        return new SfcRemoteErrorDecoder();
    }

    /** Feign 表单编码器 */
    @Bean
    public Encoder feignFormEncoder() {
        return new SpringFormEncoder();
    }

    /**
     * sfc-core 网盘文件操作统一客户端（对外唯一调用入口）。
     *
     * @param feignClient        Feign 链路实现：列表 / 下载 / 重命名 / 复制 / 移动 / 删除
     * @param properties         客户端配置项
     * @param loadBalancerClient 服务发现选路客户端
     * @return 统一入口客户端
     */
    @Bean
    @ConditionalOnMissingBean
    public SfcCoreFileClient sfcCoreFileClient(SfcCoreFileFeignClient feignClient,
                                               SfcRemoteClientProperties properties,
                                               LoadBalancerClient loadBalancerClient) {
        return new SfcCoreFileClient(feignClient, buildStreamFileClient(properties, loadBalancerClient));
    }

    /**
     * 构建流式上传客户端（包内实现，不注册为 bean）。
     *
     * @param properties         客户端配置项
     * @param loadBalancerClient 服务发现选路客户端
     * @return 流式上传客户端
     */
    private SfcCoreStreamFileClient buildStreamFileClient(SfcRemoteClientProperties properties,
                                                          LoadBalancerClient loadBalancerClient) {
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
                .requestInitializers(initializers -> initializers.add(new SfcCredentialRequestInitializer()))
                .defaultStatusHandler(new SfcRemoteResponseErrorHandler())
                .build();

        return new SfcCoreStreamFileClient(restClient);
    }
}
