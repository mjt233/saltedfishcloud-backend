package com.sfc.gateway.support;

import lombok.Getter;

/**
 * 代理判定的"拒绝"结果：由 {@link com.sfc.gateway.service.ProxyPolicyService} 抛出，
 * 由 {@link com.sfc.gateway.filter.ProxyRequestFilter} 翻译成对外的
 * <b>HTTP 状态码 ＋ 业务码</b>（形态与 core 的 {@code JsonException} 一致）。
 *
 * <p>
 * 为什么做成异常而不是"返回一个失败对象"：判定是<b>短路</b>语义——方法非法就不该继续看路径，
 * 路径非法就不该去查注册中心。异常让每条规则都能在最靠近判定的地方直接终止，
 * 不必把"失败"当成一种需要逐层传递的正常返回值。
 * </p>
 *
 * <p>
 * ⚠️ 本异常<b>不</b>继承任何咸鱼云类型：网关零咸鱼云依赖，对外的错误契约（业务码 7000 / 7001 / 7004 / 7005）
 * 按文档各自声明。
 * </p>
 */
@Getter
public class ProxyRejectException extends RuntimeException {

    /** 对外 HTTP 状态码 */
    private final int status;

    /** 对外业务码（见 {@link GatewayErrorCode}） */
    private final int businessCode;

    /**
     * 构造器。
     *
     * @param status       HTTP 状态码
     * @param businessCode 业务码
     * @param message      提示信息（会原样写进响应体）
     */
    public ProxyRejectException(int status, int businessCode, String message) {
        super(message);
        this.status = status;
        this.businessCode = businessCode;
    }
}
