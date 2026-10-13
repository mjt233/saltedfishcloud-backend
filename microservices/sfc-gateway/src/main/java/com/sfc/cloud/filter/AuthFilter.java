package com.sfc.cloud.filter;

import com.sfc.cloud.config.SfcGatewayProperties;
import com.sfc.cloud.model.UserPrincipal;
import com.sfc.cloud.trust.SfcTrustHeaders;
import com.sfc.cloud.utils.SecureUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

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
        String token = SecureUtils.getToken(exchange.getRequest());
        UserPrincipal userPrincipal = token == null ? null : SecureUtils.getAndValidUserToken(token, gatewayProperties.getJwtSecret());

        ServerHttpRequest req = exchange.getRequest().mutate()
                // 验证token是否有效，如果有效则设置X-Sfc-Uid为该token对应用户的id，否则设置为“0”。
                .header(SfcTrustHeaders.UID, userPrincipal == null ? SfcTrustHeaders.ANONYMOUS_UID : String.valueOf(userPrincipal.getId()))

                // 向微服务 表明请求来自网关
                .header(SfcTrustHeaders.FROM_GATEWAY, SfcTrustHeaders.GATEWAY_NAME)
                .build();
        return chain.filter(exchange.mutate().request(req).build());
    }
}
