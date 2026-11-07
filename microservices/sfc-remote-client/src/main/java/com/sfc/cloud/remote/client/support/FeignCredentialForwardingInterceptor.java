package com.sfc.cloud.remote.client.support;

import com.sfc.cloud.constants.SfcRemoteConstants;
import com.sfc.cloud.trust.SfcTrustHeaders;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

/**
 * 凭证透传拦截器（Feign）。
 */
@Slf4j
public class FeignCredentialForwardingInterceptor implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate template) {
        HttpServletRequest request = SfcCredentialResolver.currentRequest();
        if (request == null) {
            return;
        }
        forwardToken(request, template);
        forwardUid(request, template);
    }

    /** 透传用户凭证 */
    private void forwardToken(HttpServletRequest request, RequestTemplate template) {
        if (hasHeader(template, SfcRemoteConstants.HEADER_TOKEN)) {
            return;
        }
        String token = SfcCredentialResolver.resolveToken(request);
        if (token == null) {
            return;
        }
        // 凭证是敏感数据：不记录其值
        template.header(SfcRemoteConstants.HEADER_TOKEN, token);
        log.debug("[sfc-remote] 已透传入站凭证");
    }

    /** 透传用户身份头 */
    private void forwardUid(HttpServletRequest request, RequestTemplate template) {
        if (hasHeader(template, SfcTrustHeaders.UID)) {
            return;
        }
        String uid = SfcCredentialResolver.resolveUid(request);
        if (uid != null) {
            template.header(SfcTrustHeaders.UID, uid);
        }
    }

    /** 请求模板是否已包含指定请求头 */
    private boolean hasHeader(RequestTemplate template, String name) {
        return template.headers().keySet().stream().anyMatch(key -> key.equalsIgnoreCase(name));
    }
}
