package com.sfc.download.engine.p2p;

/**
 * aria2 JSON-RPC 调用异常。
 *
 * <p>用于把 aria2 返回的 {@code {"error": {"code": ..., "message": ...}}} 以及传输层失败
 * （连接失败、超时、非 JSON 响应等）统一表达为一个运行时异常，由调用方
 * （{@link Aria2DownloadEngine}）转换为面向用户的业务异常。</p>
 *
 * <p><b>错误码来源：</b>{@link #getCode()} 取 aria2 协议中的错误码
 * （例如 {@code 1} 表示未知方法或参数错误、{@code 3} 表示资源不存在）；
 * 传输层失败等无法取得协议错误码的场景使用 {@link #UNKNOWN_CODE}。</p>
 */
public class Aria2RpcException extends RuntimeException {

    /**
     * 无法从 aria2 协议中取得错误码时使用的占位值。
     */
    public static final int UNKNOWN_CODE = -1;

    /**
     * aria2 协议错误码（或 {@link #UNKNOWN_CODE}）。
     */
    private final int code;

    /**
     * 以「错误码 + 消息」构造异常。
     *
     * @param code    aria2 协议错误码，无法取得时传 {@link #UNKNOWN_CODE}
     * @param message 可读的错误描述
     */
    public Aria2RpcException(int code, String message) {
        super(message);
        this.code = code;
    }

    /**
     * 以「错误码 + 消息 + 根因」构造异常。
     *
     * @param code    aria2 协议错误码，无法取得时传 {@link #UNKNOWN_CODE}
     * @param message 可读的错误描述
     * @param cause   触发本次失败的根因
     */
    public Aria2RpcException(int code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    /**
     * 获取 aria2 协议错误码。
     *
     * @return 错误码；传输层失败时为 {@link #UNKNOWN_CODE}
     */
    public int getCode() {
        return code;
    }
}
