package com.sfc.cloud.filter;

import com.sfc.cloud.auth.SfcInternalAuthConstants;
import com.sfc.cloud.config.SfcGatewayProperties;
import com.sfc.cloud.model.UserPrincipal;
import com.sfc.cloud.trust.SfcTrustHeaders;
import com.sfc.cloud.utils.SecureUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * 网关全局过滤器：本地验签用户凭证并注入信任头，同时拒绝客户端访问内部接口。
 *
 * <p><b>内部接口（{@link SfcInternalAuthConstants#PATH_PREFIX}**）一律在网关层拒绝</b>：
 * 该前缀下的接口只允许微服务间调用，而服务间调用走 {@code lb://sfc-core} 直连 core、不经网关，
 * 因此网关对这一整段前缀说"不"不会误伤任何合法调用。（core 侧另有 SDK 的凭证校验兜底，
 * 见 {@code sfc-auth} 的 {@code SfcInternalAuthFilter}。）</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthFilter implements GlobalFilter, InitializingBean {

    private final SfcGatewayProperties gatewayProperties;

    @Override
    public void afterPropertiesSet() throws Exception {
        String secret = gatewayProperties.getJwtSecret();
        if (!StringUtils.hasText(secret)) {
            throw new IllegalArgumentException("JWT secret not set. Please set environment variable SFC_JWT_SECRET or property sfc.gateway.jwt-secret.");
        }
        if (secret.length() != 32) {
            throw new IllegalArgumentException("JWT secret length must be 32 characters.");
        }
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = stripPathParameters(exchange.getRequest().getURI().getPath());
        if (isInternalPath(path)) {
            log.warn("[网关] 拒绝客户端访问内部接口：path={}", exchange.getRequest().getURI().getPath());
            return rejectInternal(exchange);
        }

        String token = SecureUtils.getToken(exchange.getRequest());
        UserPrincipal userPrincipal = token == null ? null : SecureUtils.getAndValidUserToken(token, gatewayProperties.getJwtSecret());

        ServerHttpRequest req = exchange.getRequest().mutate()
                // 剥离客户端自带的内部接口凭证头：它只能由集群内的服务调用自身携带，不能从客户端流入
                .headers(headers -> headers.remove(SfcInternalAuthConstants.Header.INTERNAL_TOKEN))

                // 验证token是否有效，如果有效则设置X-Sfc-Uid为该token对应用户的id，否则设置为“0”。
                .header(SfcTrustHeaders.UID, userPrincipal == null ? SfcTrustHeaders.ANONYMOUS_UID : String.valueOf(userPrincipal.getId()))

                // 向微服务 表明请求来自网关
                .header(SfcTrustHeaders.FROM_GATEWAY, SfcTrustHeaders.GATEWAY_NAME)
                .build();
        return chain.filter(exchange.mutate().request(req).build());
    }

    /**
     * 判断路径是否落在内部接口前缀下。
     *
     * @param path 已剥离路径参数的请求路径
     * @return true 表示该请求属于仅允许服务间调用的内部接口
     */
    private boolean isInternalPath(String path) {
        return StringUtils.hasLength(path)
                && (path.equals(SfcInternalAuthConstants.PATH_PREFIX)
                || path.startsWith(SfcInternalAuthConstants.PATH_PREFIX + "/"));
    }

    /**
     * 剥离 {@code ;} 起始的路径参数段：{@code /api/internal;x/file} → {@code /api/internal/file}。
     * 与 core 侧 SDK 过滤器（{@code SfcInternalAuthFilter}）的归一化口径一致，避免该形态漏过网关层判定。
     *
     * @param path 原始请求路径
     * @return 剥离路径参数后的路径；不需要处理时原样返回
     */
    private String stripPathParameters(String path) {
        if (!StringUtils.hasLength(path) || path.indexOf(';') < 0) {
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
                sb.append(c);
            } else if (!inPathParam) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * 以 403 + JSON 信封拒绝访问内部接口。
     *
     * @param exchange 当前请求上下文
     * @return 响应写出完成信号
     */
    private Mono<Void> rejectInternal(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.FORBIDDEN);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        DataBuffer buffer = response.bufferFactory()
                .wrap(SfcInternalAuthConstants.REJECT_BODY.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }
}
