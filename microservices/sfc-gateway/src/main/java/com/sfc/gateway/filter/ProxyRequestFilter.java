package com.sfc.gateway.filter;

import com.sfc.gateway.auth.GatewayTokenVerifier;
import com.sfc.gateway.auth.TokenExtractor;
import com.sfc.gateway.config.SfcGatewayProperties;
import com.sfc.gateway.model.ProxyTarget;
import com.sfc.gateway.service.ProxyPolicyService;
import com.sfc.gateway.support.GatewayErrorBody;
import com.sfc.gateway.support.GatewayErrorCode;
import com.sfc.gateway.support.ProxyRejectException;
import com.sfc.gateway.support.SfcTrustHeaders;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.nio.charset.StandardCharsets;

/**
 * 代理分支（{@code /api/ext/**}）的核心 filter：一次请求完成"验签 → 本地判定 → 注入身份 → 改道"。
 *
 * <h3>四个步骤</h3>
 * <ol>
 *     <li><b>验签</b>：从 {@code Token} 头 / {@code ?Token=} / {@code token} Cookie 三者之一取凭证，
 *     用<b>环境变量注入的密钥</b>本地验签（{@link GatewayTokenVerifier}），得到 uid。
 *     失败 → {@code 403 / 7005}。<b>不再向 core 询问会话</b>（{@code POST /api/gateway/resolve} 已删除）；</li>
 *     <li><b>本地判定</b>：路径 / 方法 / 服务名 / 自我转发阻断 / 实例列表 / {@code sfc.api.prefix}
 *     （{@link ProxyPolicyService}），得到目标服务与下游路径；失败 → {@code 400 / 7000} 或 {@code 404 / 7001}；</li>
 *     <li><b>注入身份</b>：写入 {@code X-Sfc-Uid}，值<b>只能</b>来自第 1 步的验签结果（入站的同名头已被
 *     {@link TrustHeaderSanitizeFilter} 无条件剥离）；</li>
 *     <li><b>改道</b>：请求路径换成判定算出的 {@code targetPath}，目标服务用 {@code lb://{serviceName}}
 *     交给官方的负载均衡 filter。</li>
 * </ol>
 *
 * <h3>⚠️ 与早期实现的关键差异：凭证不再被剥离，而是原样透传</h3>
 * <p>
 * 早期实现会剥离 {@code Token} 头 / {@code token} Cookie / {@code ?Token=}（理由是"微服务不该拿到通往 core
 * 的钥匙"）。现在改为<b>三者全部原样透传</b>，原因是校验责任的划分变了：
 * 网关只做<b>验签 ＋ 过期</b>（不查 Redis 会话白名单），而下游服务需要凭证原值才能完成自己的
 * <b>完整</b>校验（core 的完整校验含会话白名单）。透传意味着微服务及其访问日志会看到长效凭证，
 * 因此"微服务必须禁止打印凭证"是一条部署侧硬约束（见 {@code docs/plan/todo.md}）。
 * </p>
 *
 * <h3>为什么这些动作放在同一个 filter 里</h3>
 * <p>
 * 拆成多个 filter 会引入"注入了但还没判定"这类中间态风险（顺序一改就破防）。合成一次请求变更
 * （{@code mutate()}）之后，身份注入与改道是同一个对象上的原子结果：要么全都生效，
 * 要么整条请求就不转发。
 * </p>
 *
 * <h3>顺序为什么必须落在 10000 与 10150 之间</h3>
 * <p>
 * {@code RouteToRequestUrlFilter}（10000）先把路由 URI 摊平成请求 URL，本 filter（10100）
 * 覆盖它并换成 {@code lb://{serviceName}{targetPath}}，随后官方的
 * {@code ReactiveLoadBalancerClientFilter}（10150）才把 {@code lb://} 换成真实实例地址。
 * 早于 10000 会被覆盖掉，晚于 10150 则来不及选实例。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProxyRequestFilter implements GlobalFilter, Ordered {

    /** 过滤器顺序：在 RouteToRequestUrlFilter(10000) 之后、ReactiveLoadBalancerClientFilter(10150) 之前 */
    public static final int ORDER = 10100;

    /** 网关配置 */
    private final SfcGatewayProperties properties;

    /** 本地验签器：唯一身份来源 */
    private final GatewayTokenVerifier tokenVerifier;

    /** 本地策略判定 */
    private final ProxyPolicyService policyService;

    /** 网关自有错误体构造（与兜底异常处理器共用同一形态） */
    private final GatewayErrorBody errorBody;

    /**
     * 处理代理分支请求。
     *
     * @param exchange 当前交换
     * @param chain    过滤器链
     * @return 完成信号
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        if (route == null || !properties.getProxyRouteId().equals(route.getId())) {
            // 非代理分支：core 透传链路必须完全透明，一个字节都不改
            return chain.filter(exchange);
        }
        ServerHttpRequest request = exchange.getRequest();
        String rawPath = request.getURI().getRawPath();
        String rawQuery = request.getURI().getRawQuery();

        // ① 本地验签：网关是唯一身份来源，凭证原值只被读取、不被改写
        Long uid = tokenVerifier.verify(TokenExtractor.extract(request));
        if (uid == null) {
            return reject(exchange, HttpStatus.FORBIDDEN.value(), GatewayErrorCode.NO_SESSION, "未登录或会话已失效");
        }

        // ② 本地判定（纯字符串规则同步抛，注册中心查询在 boundedElastic 上异步完成）
        Mono<ProxyTarget> target;
        try {
            String method = request.getMethod().name();
            target = policyService.resolve(rawPath, method);
        } catch (ProxyRejectException e) {
            return reject(exchange, e);
        }

        // ③④ 注入身份并改道；判定失败（含下层异步抛出）在响应提交前翻译成契约错误
        return target
                .flatMap(resolved -> forward(exchange, chain, resolved, uid, rawQuery))
                .onErrorResume(ProxyRejectException.class, e -> reject(exchange, e));
    }

    /**
     * 按判定结果改写请求并转发。
     *
     * @param exchange 当前交换
     * @param chain    过滤器链
     * @param target   代理目标
     * @param uid      验签得到的用户 id
     * @param rawQuery 原始查询串（<b>原样保留</b>，含 {@code ?Token=}）
     * @return 完成信号
     */
    private Mono<Void> forward(ServerWebExchange exchange, GatewayFilterChain chain,
                               ProxyTarget target, Long uid, String rawQuery) {
        String targetPath = target.targetPath();
        String suffix = StringUtils.hasText(rawQuery) ? "?" + rawQuery : "";

        // 请求自身的 URI 只改 path/query；真正的目标地址放在 GATEWAY_REQUEST_URL_ATTR 里交给负载均衡 filter。
        // 使用 URI.create（不做编码）以保持"原始字符串"语义——判定阶段已保证路径只含合法转义。
        URI requestUri;
        URI targetUri;
        try {
            requestUri = URI.create(targetPath + suffix);
            targetUri = URI.create("lb://" + target.serviceName() + targetPath + suffix);
        } catch (IllegalArgumentException e) {
            // 查询串里的非法构造（如非法的百分号转义）会让 URI 构造失败：这是调用方的输入问题，
            // 必须是 400 / 7000，而不是"网关内部错误 500"
            throw new ProxyRejectException(HttpStatus.BAD_REQUEST.value(), GatewayErrorCode.PATH_INVALID,
                    "请求路径或查询串非法");
        }

        ServerHttpRequest mutated = exchange.getRequest().mutate()
                .uri(requestUri)
                // ⚠️ 这里【只】写入身份头：Token 头 / token Cookie / ?Token= 全部原样透传给目标微服务
                .headers(headers -> headers.set(SfcTrustHeaders.UID, String.valueOf(uid)))
                .build();
        exchange.getAttributes().put(ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR, targetUri);

        if (log.isDebugEnabled()) {
            log.debug("[代理] uid={} -> lb://{}{}{}", uid, target.serviceName(), targetPath, suffix);
        }
        return chain.filter(exchange.mutate().request(mutated).build());
    }

    /**
     * 把判定/验签的拒绝结果写成契约形态的响应。
     *
     * @param exchange 当前交换
     * @param e        拒绝原因
     * @return 完成信号
     */
    private Mono<Void> reject(ServerWebExchange exchange, ProxyRejectException e) {
        return reject(exchange, e.getStatus(), e.getBusinessCode(), e.getMessage());
    }

    /**
     * 写出契约形态的错误响应（{@code {code, data, msg, businessCode}}，与 core 一致）。
     *
     * @param exchange     当前交换
     * @param status       HTTP 状态码
     * @param businessCode 业务码
     * @param message      提示信息
     * @return 完成信号
     */
    private Mono<Void> reject(ServerWebExchange exchange, int status, int businessCode, String message) {
        ServerHttpResponse response = exchange.getResponse();
        if (response.isCommitted()) {
            // 响应已开始写出（例如流式下载中途）：只能交给容器断连，不能再改状态码
            return Mono.error(new ProxyRejectException(status, businessCode, message));
        }
        response.setStatusCode(HttpStatus.valueOf(status));
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        if (log.isDebugEnabled()) {
            log.debug("[代理] 拒绝 {} {} -> {} / {}（{}）", exchange.getRequest().getMethod(),
                    exchange.getRequest().getURI().getRawPath(), status, businessCode, message);
        }
        byte[] body = errorBody.json(status, businessCode, message).getBytes(StandardCharsets.UTF_8);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body)));
    }

    /**
     * 顺序：{@value #ORDER}。
     *
     * @return 顺序值
     */
    @Override
    public int getOrder() {
        return ORDER;
    }
}
