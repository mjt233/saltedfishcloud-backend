package com.xiaotao.saltedfishcloud.constant.error;

import lombok.Getter;

@Getter
public enum CommonError implements ErrorInfo {
    // 通用模块
    FORMAT_ERROR(2001, 400, "格式错误"),
    INVALID_FILE_NAME(2002, 400, "无效的文件名"), // 系统模块
    SYSTEM_BUSY(4000, 500, "系统繁忙"), SYSTEM_FORBIDDEN(4001, 403, "权限不足"), SYSTEM_ERROR(4002, 500, "系统错误");

    int code;
    int status;
    String message;

    CommonError(int code, int status, String message) {
        this.code = code;
        this.status = status;
        this.message = message;
    }
}
