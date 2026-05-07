package com.xiaotao.saltedfishcloud.constant.error;

import lombok.Getter;

@Getter
public enum ShareError implements ErrorInfo {
    // 分享模块
    SHARE_NOT_FOUND(5000, 404, "分享不存在"),
    SHARE_EXTRACT_ERROR(5001, 400, "提取码错误"),
    SHARE_EXPIRED(5002, 404, "分享已过期"),
    SHARE_INVALID(5003, 404, "分享已失效");

    private final int code;
    private final int status;
    private final String message;

    ShareError(int code, int status, String message) {
        this.code = code;
        this.status = status;
        this.message = message;
    }
}
