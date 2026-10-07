package com.xiaotao.saltedfishcloud.service.auth;

/**
 * 委托令牌校验失败异常。
 * <p>
 * 语义上等价于"凭证无效"：验签失败、已过期、{@code iss} 不符、{@code aud} 不符、
 * 缺少必需的 claim、格式无法解析，一律抛出本异常。
 * </p>
 * <p>
 * 刻意<b>不对外区分具体原因</b>：调用方统一按 401 处理，
 * 避免通过错误信息差异探测令牌的哪一部分校验未通过。
 * </p>
 */
public class InvalidDelegationTokenException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * 构造委托令牌校验失败异常。
     *
     * @param message 面向排查者的具体原因（仅用于服务端日志，不返回给调用方）
     */
    public InvalidDelegationTokenException(String message) {
        super(message);
    }

    /**
     * 构造委托令牌校验失败异常。
     *
     * @param message 面向排查者的具体原因（仅用于服务端日志，不返回给调用方）
     * @param cause   底层原因
     */
    public InvalidDelegationTokenException(String message, Throwable cause) {
        super(message, cause);
    }
}
