package com.sfc.cloud.auth.client;

import com.sfc.cloud.auth.SfcInternalAuthConstants;
import com.sfc.cloud.auth.SfcInternalTokenIssuer;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import lombok.RequiredArgsConstructor;

/**
 * 服务间调用凭证拦截器（Feign）：为<b>每一次</b>出站请求新签一个内部调用凭证。
 *
 * <p>与用户凭证透传拦截器是两回事：这里发送的是<b>服务身份</b>，与入站请求、登录用户都无关，
 * 因此不读取任何入站上下文；凭证只应发往 core 的内部接口——请把它挂在<b>面向 core 的客户端级配置</b>上
 * （{@code @FeignClient(configuration = ...)}），不要注册成全局拦截器，否则会把共享密钥签发能力扩散到其他服务的调用上。</p>
 *
 * <p>日志中<b>绝不</b>打印凭证值。</p>
 */
@RequiredArgsConstructor
public class SfcInternalTokenFeignInterceptor implements RequestInterceptor {

    /** 凭证签发器 */
    private final SfcInternalTokenIssuer issuer;

    @Override
    public void apply(RequestTemplate template) {
        if (template.headers().keySet().stream()
                .anyMatch(key -> key.equalsIgnoreCase(SfcInternalAuthConstants.Header.INTERNAL_TOKEN))) {
            return;
        }
        template.header(SfcInternalAuthConstants.Header.INTERNAL_TOKEN, issuer.issue());
    }
}
