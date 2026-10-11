package com.sfc.gateway.support;

/**
 * 代理链路错误业务码（对外的唯一声明点）。
 *
 * <p>
 * ⚠️ 本副本原先与 core 侧 {@code com.xiaotao.saltedfishcloud.constant.error.GatewayError} 成对存在，
 * <b>该枚举已随 resolve 端点一起删除</b>：自"网关独立验签"起，代理链路上的判定全部发生在网关侧，
 * 四个码（7000 / 7001 / 7004 / 7005）都由网关自己产出，core 不再参与。契约仍是对外契约，
 * 文案见 {@code microservice-phase3-frontend-integration.md §7.7}。
 * </p>
 *
 * <p>
 * 响应体形态与 core 其他接口完全一致（{@code {code, data, msg, businessCode}}，见 {@link GatewayErrorBody}），
 * 前端因此不需要为"网关错误"和"core 错误"写两套解析。
 * </p>
 *
 * <p>
 * 历史记录：曾有 {@code 7002}（代理功能未启用，由 core 的 {@code sys.service.gateway.enabled} 判定）。
 * 该配置与端点已一并删除，<b>7002 从契约中移除</b>——代理能力现在完全由网关自己掌握，
 * 不存在"网关在、但代理功能被 core 关掉"这一形态。7003 保留，仅用于把<b>框架层</b>抛出的 413
 * 归一化成契约形态。
 * </p>
 */
public final class GatewayErrorCode {

    /** 路径或方法非法：编码的斜杠 / 反斜杠 / NUL / 点段、重复斜杠、控制字符、不支持的方法等 */
    public static final int PATH_INVALID = 7000;

    /** 目标服务不可代理：未注册、无健康实例，或目标就是 core / 网关自身 */
    public static final int SERVICE_NOT_AVAILABLE = 7001;

    /**
     * 请求体被框架层判定为过大（HTTP 413）时的契约归一化。
     *
     * <p>
     * ⚠️ 网关<b>不</b>管控请求体大小：既不设全局上限，也没有请求体计数 filter（早期的
     * {@code ProxyRequestBodyLimitFilter} 已按决策删除）。请求体多大由承载业务的服务自己决定
     * ——core 的 multipart 上限是 8GB，网关只做<b>流式通道</b>，不缓冲、不计数、不截断。
     * 保留本码只为让框架层抛出的 413 仍以契约形态返回；<b>上游自己返回的 413 响应</b>
     * 由代理链路原样透传，不经过这里。
     * </p>
     */
    public static final int PAYLOAD_TOO_LARGE = 7003;

    /** 目标服务实例全部连不通（连接失败 / 连接超时 / 重试耗尽） */
    public static final int UPSTREAM_UNAVAILABLE = 7004;

    /** 未登录 / 会话无效（凭证缺失、验签失败或已过期） */
    public static final int NO_SESSION = 7005;

    private GatewayErrorCode() {
        // 常量容器，禁止实例化
    }
}
