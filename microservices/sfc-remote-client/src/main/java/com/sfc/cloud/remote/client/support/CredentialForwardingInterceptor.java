package com.sfc.cloud.remote.client.support;

import com.sfc.cloud.remote.constants.SfcRemoteConstants;
import com.sfc.cloud.trust.SfcTrustHeaders;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 凭证透传拦截器。
 */
@Slf4j
public class CredentialForwardingInterceptor implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate template) {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servletAttributes)) {
            return;
        }
        HttpServletRequest request = servletAttributes.getRequest();
        forwardToken(request, template);
        forwardUid(request, template);
    }

    /** 透传用户凭证 */
    private void forwardToken(HttpServletRequest request, RequestTemplate template) {
        if (hasHeader(template, SfcRemoteConstants.HEADER_TOKEN)) {
            return;
        }
        String token = resolveToken(request);
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
        String uid = request.getHeader(SfcTrustHeaders.UID);
        if (uid != null && !uid.isBlank()) {
            template.header(SfcTrustHeaders.UID, uid);
        }
    }

    /** 解析入站凭证：请求头、查询参数、Cookie */
    private String resolveToken(HttpServletRequest request) {
        String token = request.getHeader(SfcRemoteConstants.HEADER_TOKEN);
        if (token == null && !isMultipart(request)) {
            token = request.getParameter(SfcRemoteConstants.PARAM_TOKEN);
        }
        if (token == null) {
            Cookie[] cookies = request.getCookies();
            if (cookies != null) {
                for (Cookie cookie : cookies) {
                    if (SfcRemoteConstants.COOKIE_TOKEN.equals(cookie.getName())) {
                        token = cookie.getValue();
                    }
                }
            }
        }
        return token == null || token.isBlank() ? null : token;
    }

    /** 是否为 multipart 请求 */
    private boolean isMultipart(HttpServletRequest request) {
        String contentType = request.getContentType();
        return contentType != null && contentType.toLowerCase().startsWith("multipart/");
    }

    /** 请求模板是否已包含指定请求头 */
    private boolean hasHeader(RequestTemplate template, String name) {
        return template.headers().keySet().stream().anyMatch(key -> key.equalsIgnoreCase(name));
    }
}
