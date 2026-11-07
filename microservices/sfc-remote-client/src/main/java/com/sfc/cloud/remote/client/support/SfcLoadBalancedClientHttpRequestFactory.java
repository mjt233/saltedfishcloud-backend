package com.sfc.cloud.remote.client.support;

import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.AbstractClientHttpRequestFactoryWrapper;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URI;
import java.util.Objects;

/**
 * 服务发现（负载均衡）请求工厂：把以服务名书写的目标地址（如 {@code lb://sfc-core/xxx}）
 * 改写成 {@link LoadBalancerClient} 选出的真实实例地址后，再交给被装饰的请求工厂创建请求。
 * 不使用系统自带的RestClient的拦截器而采用这个更底层的RequestFactory是防止RestClient在请求体写出前就把整个请求体缓冲进内存，导致大文件上传时内存占用过高。
 */
@Slf4j
public class SfcLoadBalancedClientHttpRequestFactory extends AbstractClientHttpRequestFactoryWrapper {

    /** 负载均衡选路器（必填） */
    private final LoadBalancerClient loadBalancerClient;

    /**
     * 构造服务发现请求工厂。
     *
     * @param requestFactory     被装饰的请求工厂（决定请求体的写出方式）
     * @param loadBalancerClient 负载均衡选路器，不能为 null
     */
    public SfcLoadBalancedClientHttpRequestFactory(ClientHttpRequestFactory requestFactory,
                                                   LoadBalancerClient loadBalancerClient) {
        super(requestFactory);
        this.loadBalancerClient = Objects.requireNonNull(loadBalancerClient, "loadBalancerClient 不能为 null");
    }

    @Override
    @NonNull
    protected ClientHttpRequest createRequest(@NonNull URI uri, @NonNull HttpMethod httpMethod, @NonNull ClientHttpRequestFactory requestFactory)
            throws IOException {
        URI target = uri;
        if("lb".equals(uri.getScheme())) {
            target = resolveTargetUri(uri);
            if (log.isDebugEnabled() && !target.equals(uri)) {
                log.debug("[sfc-remote] 目标地址重写：{} -> {}", uri, target);
            }
        }
        return requestFactory.createRequest(target, httpMethod);
    }

    /**
     * 按服务名选取实例，并用实例地址替换请求 URI 的 scheme / host / port。
     *
     * @param uri 调用方书写的地址
     * @return 指向真实实例的地址
     */
    private URI resolveTargetUri(URI uri) {
        ServiceInstance instance = loadBalancerClient.choose(uri.getHost());
        URI base = instance.getUri();
        return UriComponentsBuilder.fromUri(uri)
                .scheme(base.getScheme())
                .host(base.getHost())
                .port(base.getPort())
                .build(true)
                .toUri();
    }
}
