package com.sfc.gateway.handler;

import com.sfc.gateway.support.GatewayErrorBody;
import com.sfc.gateway.support.GatewayErrorCode;
import io.netty.handler.timeout.ReadTimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.webflux.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.TimeoutException;

/**
 * 网关的兜底错误处理器：把"官方库的异常"翻译成<b>契约里的 HTTP 状态码 ＋ 业务码</b>。
 *
 * <h3>为什么必须自己写</h3>
 * <p>
 * 8.1 预研实测：杀掉一个微服务实例后，带请求体的写请求（不重试）在官方默认处理器下对外是
 * <b>500</b>（日志里是 {@code ConnectTimeoutException}），而契约要求"实例连不通"是
 * <b>503 / 7004</b>。同样，框架层抛出的 413 在官方响应里只是一个空体 413，没有业务码。
 * 对外契约必须稳定，不能把"哪个库抛了什么异常"暴露给前端。
 * </p>
 *
 * <h3>与其他处理器/过滤器的分工</h3>
 * <ul>
 *     <li>本地判定与验签的拒绝（403/7005、404/7001、400/7000）由
 *     {@link com.sfc.gateway.filter.ProxyRequestFilter} 自己写成契约形态，不走这里；</li>
 *     <li>这里只处理"转发阶段"的异常：连接失败、超时、框架层 413，以及其它未归类异常。</li>
 * </ul>
 *
 * <p>
 * ⚠️ 网关<b>不</b>管控请求体大小（不设上限、不做计数拦截），因此 413 只可能由框架层/上游抛出；
 * 上游自己返回的 413 是<b>响应</b>，由代理链路原样透传，不会走到这里。
 * </p>
 *
 * <p>
 * ⚠️ 响应体采用与 core 一致的形态（{@code {code, data, msg, businessCode}}），
 * 前端因此不需要为"网关错误"和"core 错误"写两套解析。
 * </p>
 */
@Slf4j
@Component
@Order(GatewayErrorWebExceptionHandler.ORDER)
public class GatewayErrorWebExceptionHandler implements ErrorWebExceptionHandler {

    /**
     * 处理器顺序：比 Spring Boot 默认的 {@code DefaultErrorWebExceptionHandler}（-1）更靠前，
     * 才能接管响应；同时不用 {@code HIGHEST_PRECEDENCE}，避免抢走其它组件期望的错误处理。
     */
    public static final int ORDER = -2;

    /** 网关自有错误体构造（与代理 filter 的拒绝分支共用同一个构造点） */
    private final GatewayErrorBody errorBody;

    /**
     * 构造器。
     *
     * @param errorBody 错误体构造器
     */
    public GatewayErrorWebExceptionHandler(GatewayErrorBody errorBody) {
        this.errorBody = errorBody;
    }

    /**
     * 处理未被消费的异常。
     *
     * @param exchange 当前交换
     * @param ex       异常
     * @return 完成信号
     */
    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        if (exchange.getResponse().isCommitted()) {
            // 响应已开始写出（例如流式下载中途失败）：只能交给容器断连
            return Mono.error(ex);
        }
        ErrorMapping mapping = map(ex);
        if (mapping.status >= 500) {
            log.error("[网关错误] {} {} -> {} / {}", exchange.getRequest().getMethod(),
                    exchange.getRequest().getURI().getRawPath(), mapping.status, mapping.businessCode, ex);
        } else {
            log.warn("[网关拒绝] {} {} -> {} / {}（{}）", exchange.getRequest().getMethod(),
                    exchange.getRequest().getURI().getRawPath(), mapping.status, mapping.businessCode,
                    mapping.message);
        }

        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.valueOf(mapping.status));
        if (isWebSocketUpgrade(exchange)) {
            // WebSocket 握手失败没有"响应体"可写：直接结束连接，由客户端表现为握手失败
            return response.setComplete();
        }
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] body = buildBody(mapping).getBytes(StandardCharsets.UTF_8);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body)));
    }

    /**
     * 把异常翻译成状态码 ＋ 业务码。
     *
     * @param ex 异常
     * @return 映射结果
     */
    private ErrorMapping map(Throwable ex) {
        // 注意：413 的异常类型在 Spring 7 里改名了（PayloadTooLargeException → ContentTooLargeException，
        // 常量同理），但两者都是 ResponseStatusException 且状态码为 413，因此这里统一按状态码判定，
        // 不绑定具体异常类型——既覆盖新旧两种，也不必引用已废弃的 API。
        // 网关自身不产生 413（不设请求体上限），这一分支只为"框架层抛出的 413"兜住契约形态。
        if (ex instanceof ResponseStatusException statusException) {
            int status = statusException.getStatusCode().value();
            if (status == HttpStatus.CONTENT_TOO_LARGE.value()) {
                return new ErrorMapping(status, GatewayErrorCode.PAYLOAD_TOO_LARGE, "请求体超出上限");
            }
            if (status == HttpStatus.SERVICE_UNAVAILABLE.value() || status == HttpStatus.GATEWAY_TIMEOUT.value()) {
                return new ErrorMapping(HttpStatus.SERVICE_UNAVAILABLE.value(), GatewayErrorCode.UPSTREAM_UNAVAILABLE,
                        "目标服务实例不可达");
            }
            return new ErrorMapping(status, 0,
                    statusException.getReason() == null ? "网关请求失败" : statusException.getReason());
        }
        if (isConnectionFailure(ex)) {
            return new ErrorMapping(HttpStatus.SERVICE_UNAVAILABLE.value(), GatewayErrorCode.UPSTREAM_UNAVAILABLE,
                    "目标服务实例不可达");
        }
        return new ErrorMapping(HttpStatus.INTERNAL_SERVER_ERROR.value(), 0, "网关内部错误");
    }

    /**
     * 判断异常链里是否存在"连不通 / 读超时"这类上游故障。
     *
     * <p>
     * 只需覆盖三类即可：{@link IOException}（含 {@code ConnectException} 与 Netty 的
     * {@code ConnectTimeoutException}——后者是前者的子类）、{@link TimeoutException}、
     * 以及 Netty 自己的 {@link ReadTimeoutException}（它不继承 JDK 的 TimeoutException）。
     * </p>
     *
     * @param ex 异常
     * @return 属于上游故障时返回 true
     */
    private boolean isConnectionFailure(Throwable ex) {
        Throwable current = ex;
        while (current != null) {
            if (current instanceof IOException
                    || current instanceof TimeoutException
                    || current instanceof ReadTimeoutException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    /**
     * 判断是否为 WebSocket 升级请求。
     *
     * @param exchange 当前交换
     * @return 是升级请求时返回 true
     */
    private boolean isWebSocketUpgrade(ServerWebExchange exchange) {
        String upgrade = exchange.getRequest().getHeaders().getFirst(HttpHeaders.UPGRADE);
        return upgrade != null && upgrade.toLowerCase(Locale.ROOT).contains("websocket");
    }

    /**
     * 构造与 core 同形的错误响应体。
     *
     * @param mapping 映射结果
     * @return JSON 字符串
     */
    private String buildBody(ErrorMapping mapping) {
        return errorBody.json(mapping.status, mapping.businessCode, mapping.message);
    }

    /**
     * 错误映射结果。
     *
     * @param status       HTTP 状态码
     * @param businessCode 业务码（0 表示无对应业务码）
     * @param message      提示信息
     */
    private record ErrorMapping(int status, int businessCode, String message) {
    }
}
