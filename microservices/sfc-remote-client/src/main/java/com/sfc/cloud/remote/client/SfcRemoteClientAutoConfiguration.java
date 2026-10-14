package com.sfc.cloud.remote.client;

import com.sfc.cloud.remote.client.support.CredentialForwardingInterceptor;
import com.sfc.cloud.remote.client.support.SfcRemoteErrorDecoder;
import feign.Client;
import feign.RequestInterceptor;
import feign.codec.Encoder;
import feign.codec.ErrorDecoder;
import feign.form.spring.SpringFormEncoder;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;

/**
 * 远程客户端自动装配。
 */
@AutoConfiguration
@ConditionalOnClass(Client.class)
@EnableFeignClients(clients = SfcCoreFileClient.class)
public class SfcRemoteClientAutoConfiguration {

    /** 凭证透传拦截器 */
    @Bean
    @ConditionalOnMissingBean
    public RequestInterceptor sfcCredentialForwardingInterceptor() {
        return new CredentialForwardingInterceptor();
    }

    /** core 错误信封解码器 */
    @Bean
    @ConditionalOnMissingBean
    public ErrorDecoder sfcRemoteErrorDecoder() {
        return new SfcRemoteErrorDecoder();
    }


    @Bean
    public Encoder feignFormEncoder() {
        return new SpringFormEncoder();
    }
}
