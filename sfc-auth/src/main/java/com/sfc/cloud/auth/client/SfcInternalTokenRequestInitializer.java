package com.sfc.cloud.auth.client;

import com.sfc.cloud.auth.SfcInternalAuthConstants;
import com.sfc.cloud.auth.SfcInternalTokenIssuer;
import lombok.RequiredArgsConstructor;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestInitializer;

/**
 * 服务间调用凭证初始化器（RestClient）：为<b>每一次</b>出站请求新签一个内部调用凭证。
 *
 * <p>刻意使用 {@link ClientHttpRequestInitializer} 而非 {@code ClientHttpRequestInterceptor}：
 * 后者会让 RestClient 改用带缓冲的请求工厂，流式上传大文件会整体读入内存。</p>
 *
 * <p>与用户凭证透传初始化器的关键差异：用户凭证只在<b>有入站请求上下文</b>时才有意义，
 * 而服务身份与入站上下文无关——本类不读取请求上下文，定时任务等无 HTTP 上下文的调用同样会带上凭证。</p>
 *
 * <p>日志中<b>绝不</b>打印凭证值。</p>
 */
@RequiredArgsConstructor
public class SfcInternalTokenRequestInitializer implements ClientHttpRequestInitializer {

    /** 凭证签发器 */
    private final SfcInternalTokenIssuer issuer;

    @Override
    public void initialize(ClientHttpRequest request) {
        if (request.getHeaders().containsHeader(SfcInternalAuthConstants.Header.INTERNAL_TOKEN)) {
            return;
        }
        request.getHeaders().set(SfcInternalAuthConstants.Header.INTERNAL_TOKEN, issuer.issue());
    }
}
