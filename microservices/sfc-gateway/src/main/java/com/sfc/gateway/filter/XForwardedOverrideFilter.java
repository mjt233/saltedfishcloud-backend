package com.sfc.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;

/**
 * {@code X-Forwarded-*} 与 {@code X-Real-IP} 的<b>覆盖式</b>写入。
 *
 * <h3>为什么是"覆盖"而不是"追加"</h3>
 * <p>
 * 网关是整站最外层：它看到的对端地址就是浏览器的地址。若采用"追加"（把入站值接在后面）或干脆不写，
 * 那么调用方随手塞一个 {@code X-Forwarded-For: 1.2.3.4} 就能污染下游的"来源 IP"认知——
 * "追加"与"不信任入站值"在语义上是矛盾的。
 * </p>
 *
 * <h3>为什么还要写 {@code X-Real-IP}</h3>
 * <p>
 * core 的 {@code application.yml} 里配的是 {@code server.tomcat.remoteip.remote-ip-header: X-Real-IP}：
 * 它认的是这个头，而不是 {@code X-Forwarded-For}。网关在前之后，若不同时覆盖它，
 * core 记录到的"来源 IP"会一律变成网关的地址。
 * </p>
 *
 * <h3>请求头 {@code Host} 由谁重写</h3>
 * <p>
 * 由反应式 HTTP 客户端（Reactor Netty）依据目标 URL 自动写入——实测下游看到的是实例的
 * {@code host:port}。因此这里不重复处理，只记录事实。
 * </p>
 */
@Slf4j
@Component
public class XForwardedOverrideFilter implements GlobalFilter, Ordered {

    /** {@code X-Forwarded-For} 头名 */
    public static final String X_FORWARDED_FOR = "X-Forwarded-For";

    /** {@code X-Forwarded-Host} 头名（原始 Host，供 core 生成绝对地址与 OIDC 重定向） */
    public static final String X_FORWARDED_HOST = "X-Forwarded-Host";

    /** {@code X-Forwarded-Proto} 头名 */
    public static final String X_FORWARDED_PROTO = "X-Forwarded-Proto";

    /** {@code X-Forwarded-Port} 头名 */
    public static final String X_FORWARDED_PORT = "X-Forwarded-Port";

    /** {@code X-Real-IP} 头名（core 侧 remote-ip-header 认的就是它） */
    public static final String X_REAL_IP = "X-Real-IP";

    /** 过滤器顺序：尽早执行（在所有路由与转发 filter 之前） */
    public static final int ORDER = -100;

    /**
     * 覆盖式写入转发头。
     *
     * @param exchange 当前交换
     * @param chain    过滤器链
     * @return 完成信号
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        var request = exchange.getRequest();
        String clientIp = clientIp(request.getRemoteAddress());
        String originalHost = request.getHeaders().getFirst(HttpHeaders.HOST);
        if (!StringUtils.hasText(originalHost)) {
            originalHost = request.getURI().getHost();
        }
        String proto = request.getURI().getScheme();
        Integer localPort = request.getLocalAddress() == null ? null : request.getLocalAddress().getPort();

        final String hostValue = originalHost;
        var mutated = request.mutate()
                .headers(headers -> {
                    if (StringUtils.hasText(clientIp)) {
                        headers.set(X_FORWARDED_FOR, clientIp);
                        headers.set(X_REAL_IP, clientIp);
                    }
                    if (StringUtils.hasText(hostValue)) {
                        headers.set(X_FORWARDED_HOST, hostValue);
                    }
                    if (StringUtils.hasText(proto)) {
                        headers.set(X_FORWARDED_PROTO, proto);
                    }
                    if (localPort != null) {
                        headers.set(X_FORWARDED_PORT, String.valueOf(localPort));
                    }
                })
                .build();
        if (log.isTraceEnabled()) {
            log.trace("[转发头] {} -> {} for {} host={} proto={}", request.getMethod(),
                    request.getURI().getRawPath(), clientIp, hostValue, proto);
        }
        return chain.filter(exchange.mutate().request(mutated).build());
    }

    /**
     * 取对端 IP。
     *
     * @param address 对端地址
     * @return IP 字符串，取不到时返回 null
     */
    private String clientIp(InetSocketAddress address) {
        return address == null || address.getAddress() == null ? null : address.getAddress().getHostAddress();
    }

    /**
     * 顺序：{-100}。
     *
     * @return 顺序值
     */
    @Override
    public int getOrder() {
        return ORDER;
    }
}
