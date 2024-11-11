package com.xiaotao.saltedfishcloud.constant.error;

import lombok.Getter;

@Getter
public enum CommonError implements ErrorInfo {
    // 通用模块
    FORMAT_ERROR(2001, 400, "格式错误"),
    INVALID_FILE_NAME(2002, 400, "无效的文件名"),
    NOT_ALLOW_PATH(2003, 400, "存在非法的文件名或路径"),
    SYSTEM_BUSY(4000, 500, "系统繁忙"),
    SYSTEM_FORBIDDEN(4001, 403, "权限不足"),
    SYSTEM_ERROR(4002, 500, "系统错误"),
    RESOURCE_NOT_FOUND(4003, 404, "无法根据id查询到数据"),
    MOUNT_POINT_FILE_RECORD_PROXY_ERROR(4004, 500, "挂载点文件记录委托错误");

    private final int code;
    private final int status;
    private final String message;

    CommonError(int code, int status, String message) {
        this.code = code;
        this.status = status;
        this.message = message;
    }
}
