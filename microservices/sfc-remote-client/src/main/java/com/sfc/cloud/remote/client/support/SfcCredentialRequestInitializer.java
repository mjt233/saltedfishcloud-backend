package com.sfc.cloud.remote.client.support;

import com.sfc.cloud.remote.constants.SfcRemoteConstants;
import com.sfc.cloud.trust.SfcTrustHeaders;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestInitializer;

/**
 * RestClient 凭证透传初始化器。语义与 Feign 的 {@link FeignCredentialForwardingInterceptor} 一致：
 * 把入站请求的凭证（Token 头 / 查询参数 / token Cookie）与网关注入的 {@code X-Sfc-Uid} 原样带到回调请求上，
 * 不产生、不解析、不校验任何凭证；无 HTTP 上下文时不伪造身份。
 *
 * <p>这里刻意<b>不</b>使用 {@link org.springframework.http.client.ClientHttpRequestInterceptor}：
 * {@code DefaultRestClient} 只要装配了拦截器，就会改用 {@code InterceptingClientHttpRequestFactory}，
 * 其请求继承 {@code AbstractBufferingClientHttpRequest}，会把整个请求体缓冲进内存（276MB 文件上传直接 OOM）。
 * 初始化器在请求写出之前执行，不影响流式写出。</p>
 */
@Slf4j
public class SfcCredentialRequestInitializer implements ClientHttpRequestInitializer {

    @Override
    public void initialize(@NonNull ClientHttpRequest request) {
        HttpServletRequest inbound = SfcCredentialResolver.currentRequest();
        if (inbound == null) {
            return;
        }
        forwardToken(inbound, request);
        forwardUid(inbound, request);
    }

    /**
     * 透传用户凭证。
     *
     * @param inbound 入站请求
     * @param request 出站请求
     */
    private void forwardToken(HttpServletRequest inbound, ClientHttpRequest request) {
        if (request.getHeaders().containsHeader(SfcRemoteConstants.HEADER_TOKEN)) {
            return;
        }
        String token = SfcCredentialResolver.resolveToken(inbound);
        if (token == null) {
            return;
        }
        // 凭证是敏感数据：不记录其值
        request.getHeaders().set(SfcRemoteConstants.HEADER_TOKEN, token);
        log.debug("[sfc-remote] 已透传入站凭证");
    }

    /**
     * 透传用户身份头。
     *
     * @param inbound 入站请求
     * @param request 出站请求
     */
    private void forwardUid(HttpServletRequest inbound, ClientHttpRequest request) {
        if (request.getHeaders().containsHeader(SfcTrustHeaders.UID)) {
            return;
        }
        String uid = SfcCredentialResolver.resolveUid(inbound);
        if (uid != null) {
            request.getHeaders().set(SfcTrustHeaders.UID, uid);
        }
    }
}
