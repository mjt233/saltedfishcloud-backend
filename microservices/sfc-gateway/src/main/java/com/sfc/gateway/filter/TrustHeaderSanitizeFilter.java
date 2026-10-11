package com.sfc.gateway.filter;

import com.sfc.gateway.support.SfcTrustHeaders;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 剥离入站的一切 {@code X-Sfc-*} 身份头——<b>信任模式的唯一防线</b>。
 *
 * <h3>为什么是所有路由，而不只是代理分支</h3>
 * <p>
 * 设计上要防的第一件事是"调用方自带一个 {@code X-Sfc-Uid} 冒充别人"。
 * 代理分支（{@code /api/ext/**}）当然要剥离；但如果只剥离那一条分支，
 * 通过 core 透传链路（{@code /api/**}）发出的伪造头就会直达 core——而 core 在档位 2 阶段 6
 * 会让 {@code /api/internal/**} <b>信任</b>这个头。到那时，"只剥离代理分支"就是一个真实的提权漏洞。
 * 在网关这一层无条件剥离，等于让浏览器流量<b>结构上不可能</b>携带该头。
 * </p>
 * <p>
 * 剥离子集不含任何"业务语义"：它只<b>删除</b>调用方自带的身份声明，不注入、不翻译、不放行，
 * 因此 core 透传链路"完全透明"的性质（core 自己判鉴权、零改动）不受影响。
 * </p>
 */
@Slf4j
@Component
public class TrustHeaderSanitizeFilter implements GlobalFilter, Ordered {

    /** 过滤器顺序：紧随 {@link XForwardedOverrideFilter} 之后 */
    public static final int ORDER = -99;

    /**
     * 剥离入站身份头。
     *
     * @param exchange 当前交换
     * @param chain    过滤器链
     * @return 完成信号
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        List<String> toRemove = new ArrayList<>();
        // 注意：Spring Framework 7 的 HttpHeaders 不再实现 Map（没有 keySet()），改用 headerNames()
        for (String name : request.getHeaders().headerNames()) {
            if (name.toLowerCase(Locale.ROOT).startsWith(SfcTrustHeaders.PREFIX)) {
                toRemove.add(name);
            }
        }
        if (toRemove.isEmpty()) {
            return chain.filter(exchange);
        }
        log.warn("[信任边界] 剥离入站身份头：{}（来源 {}）", toRemove, request.getRemoteAddress());
        ServerHttpRequest mutated = request.mutate()
                .headers(headers -> toRemove.forEach(headers::remove))
                .build();
        return chain.filter(exchange.mutate().request(mutated).build());
    }

    /**
     * 顺序：{-99}。
     *
     * @return 顺序值
     */
    @Override
    public int getOrder() {
        return ORDER;
    }
}
