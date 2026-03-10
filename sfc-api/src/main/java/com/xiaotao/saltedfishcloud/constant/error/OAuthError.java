package com.xiaotao.saltedfishcloud.constant.error;


import lombok.Getter;

/**
 * 开放平台相关错误
 */
@Getter
public enum OAuthError implements ErrorInfo {
    INVALID_TOKEN(60000, 400, "无效的token"),
    INVALID_CODE(60001, 400, "无效的授权码code"),
    INVALID_APP_ID(60003, 400, "无效的appId"),
    APP_DISABLED(60004, 400, "应用已被停用"),
    PERMISSION_DENIED(60005, 403, "未授权的操作"),
    CLIENT_SECRET_INVALID(60006, 400, "Client Secret验证失败");

    private final int code;
    private final int status;
    private final String message;

    OAuthError(int code, int status, String message) {
        this.code = code;
        this.status = status;
        this.message = message;
    }
}
