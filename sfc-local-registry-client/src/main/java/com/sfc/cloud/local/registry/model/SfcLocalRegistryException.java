package com.sfc.cloud.local.registry.model;

import lombok.Getter;

import java.io.Serial;

/**
 * local 注册中心调用异常。
 */
@Getter
public class SfcLocalRegistryException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

    /** 业务响应码 */
    private final int code;

    /** HTTP 状态码，0 表示未收到有效响应 */
    private final int httpStatus;

    /**
     * 以业务响应码构造异常。
     *
     * @param code    业务响应码
     * @param message 错误消息
     */
    public SfcLocalRegistryException(int code, String message) {
        this(code, 0, message);
    }

    /**
     * 以业务响应码与 HTTP 状态码构造异常。
     *
     * @param code       业务响应码
     * @param httpStatus HTTP 状态码
     * @param message    错误消息
     */
    public SfcLocalRegistryException(int code, int httpStatus, String message) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
    }

    /**
     * 是否表示实例不存在或已过期。
     *
     * @return 业务码或 HTTP 状态为 404 时返回 true
     */
    public boolean isNotFound() {
        return code == 404 || httpStatus == 404;
    }

    /**
     * 是否表示密码校验失败。
     *
     * @return 业务码或 HTTP 状态为 403 时返回 true
     */
    public boolean isForbidden() {
        return code == 403 || httpStatus == 403;
    }
}
