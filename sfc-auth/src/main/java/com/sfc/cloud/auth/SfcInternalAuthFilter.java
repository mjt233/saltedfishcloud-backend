package com.sfc.cloud.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.core.Ordered;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 服务端校验过滤器：{@link SfcInternalAuthConstants#PATH_PREFIX}** 下的请求必须携带有效的内部调用凭证。
 */
@Slf4j
@RequiredArgsConstructor
public class SfcInternalAuthFilter extends OncePerRequestFilter implements Ordered {

    /** 凭证校验器（未配置密钥时其校验恒失败，从而保证 fail-closed） */
    private final SfcInternalTokenVerifier verifier;

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        if (!isInternalRequest(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        SfcInternalTokenVerificationResult result =
                verifier.verify(request.getHeader(SfcInternalAuthConstants.Header.INTERNAL_TOKEN));
        if (result.valid()) {
            filterChain.doFilter(request, response);
            return;
        }

        log.warn("[内部接口] 拒绝非服务间调用：reason={}({}), issuer={}, jti={}, uri={}, remoteAddr={}",
                result.failure(), result.failure().getDescription(), result.issuer(), result.jti(),
                request.getRequestURI(), request.getRemoteAddr());
        reject(response);
    }

    /**
     * 判断是否为内部接口请求。任一候选路径命中即视为内部接口，宁可多拦也不错放。
     *
     * @param request 请求对象
     * @return true 表示该请求落在 {@link SfcInternalAuthConstants#PATH_PREFIX} 下
     */
    private boolean isInternalRequest(HttpServletRequest request) {
        return matchPrefix(request.getServletPath()) || matchPrefix(normalize(request.getRequestURI()));
    }

    /**
     * 判断路径是否落在内部接口前缀下。
     *
     * @param path 候选路径，可为 null
     * @return true 表示路径等于前缀本身或以 {@code 前缀/} 开头
     */
    private boolean matchPrefix(String path) {
        return StringUtils.hasLength(path)
                && (path.equals(SfcInternalAuthConstants.PATH_PREFIX)
                || path.startsWith(SfcInternalAuthConstants.PATH_PREFIX + "/"));
    }

    /**
     * 归一化原始请求 URI：丢弃 {@code ;} 起始的路径参数段、折叠重复的 {@code /}，
     * 使判定口径与容器映射前处理一致。
     *
     * @param path 原始请求 URI，可为 null
     * @return 归一化后的路径；入参不需要归一化时原样返回
     */
    private String normalize(String path) {
        if (!StringUtils.hasLength(path) || (path.indexOf(';') < 0 && !path.contains("//"))) {
            return path;
        }
        StringBuilder sb = new StringBuilder(path.length());
        boolean inPathParam = false;
        for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);
            if (c == ';') {
                inPathParam = true;
            } else if (c == '/') {
                inPathParam = false;
                if (sb.isEmpty() || sb.charAt(sb.length() - 1) != '/') {
                    sb.append(c);
                }
            } else if (!inPathParam) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * 以统一 JSON 信封拒绝请求（与 core 侧 {@code JsonResultImpl} 的形态一致）。
     *
     * @param response 响应对象
     * @throws IOException 写出响应体失败
     */
    private void reject(HttpServletResponse response) throws IOException {
        response.setContentType(SfcInternalAuthConstants.REJECT_CONTENT_TYPE);
        response.setStatus(SfcInternalAuthConstants.REJECT_STATUS);
        response.getWriter().print(SfcInternalAuthConstants.REJECT_BODY);
    }
}
