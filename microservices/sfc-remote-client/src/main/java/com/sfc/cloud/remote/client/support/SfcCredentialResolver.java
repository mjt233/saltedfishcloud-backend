package com.sfc.cloud.remote.client.support;

import com.sfc.cloud.remote.constants.SfcRemoteConstants;
import com.sfc.cloud.trust.SfcTrustHeaders;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 入站凭证解析工具。Feign 拦截器与 RestClient 拦截器共用同一套解析规则，避免两套实现产生语义漂移。
 */
final class SfcCredentialResolver {

    /** 工具类不允许实例化 */
    private SfcCredentialResolver() {
    }

    /**
     * 获取当前线程绑定的入站请求。
     *
     * @return 当前请求；无 HTTP 上下文（如定时任务、消息消费线程）时返回 null
     */
    static HttpServletRequest currentRequest() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletAttributes) {
            return servletAttributes.getRequest();
        }
        return null;
    }

    /**
     * 解析入站凭证：请求头 → 查询参数（multipart 请求不读，避免提前消费请求体）→ token Cookie。
     *
     * @param request 入站请求
     * @return 凭证原文；不存在时返回 null
     */
    static String resolveToken(HttpServletRequest request) {
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
        return (token == null || token.isBlank()) ? null : token;
    }

    /**
     * 解析网关注入的用户身份头。
     *
     * @param request 入站请求
     * @return uid 原文；不存在时返回 null
     */
    static String resolveUid(HttpServletRequest request) {
        String uid = request.getHeader(SfcTrustHeaders.UID);
        return (uid == null || uid.isBlank()) ? null : uid;
    }

    /**
     * 判断是否为 multipart 请求。
     *
     * @param request 入站请求
     * @return multipart 请求返回 true
     */
    static boolean isMultipart(HttpServletRequest request) {
        String contentType = request.getContentType();
        return contentType != null && contentType.toLowerCase().startsWith("multipart/");
    }
}
