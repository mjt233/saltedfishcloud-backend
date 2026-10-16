package com.sfc.cloud.remote;

import lombok.Getter;

/**
 * 调用 sfc-core 失败异常。
 */
@Getter
public class SfcRemoteApiException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** HTTP 状态码 */
    private final int httpStatus;

    /** 业务响应码 */
    private final int code;

    /** 细分业务码 */
    private final int businessCode;

    /** Feign 方法标识 */
    private final String methodKey;

    /** core 的提示消息 */
    private final String msg;

    public SfcRemoteApiException(int httpStatus, int code, int businessCode, String methodKey, String msg) {
        super(buildMessage(httpStatus, code, businessCode, methodKey, msg));
        this.httpStatus = httpStatus;
        this.code = code;
        this.businessCode = businessCode;
        this.methodKey = methodKey;
        this.msg = msg;
    }

    /** 是否为拒绝访问 */
    public boolean isForbidden() {
        return httpStatus == 403;
    }

    /** 是否为资源不存在 */
    public boolean isNotFound() {
        return httpStatus == 404;
    }

    /** 是否为参数错误 */
    public boolean isBadRequest() {
        return httpStatus == 400 || httpStatus == 422;
    }

    /** 拼装异常消息 */
    private static String buildMessage(int httpStatus, int code, int businessCode, String methodKey, String msg) {
        return "调用 sfc-core 失败[http=" + httpStatus + ", code=" + code + ", businessCode=" + businessCode + "]"
                + (methodKey == null ? "" : " " + methodKey)
                + (msg == null ? "" : "：" + msg);
    }
}
