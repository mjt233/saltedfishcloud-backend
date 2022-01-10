package com.xiaotao.saltedfishcloud.constant.error;

import lombok.Getter;

@Getter
public enum FileSystemError implements ErrorInfo {
    NODE_NOT_FOUND(1000, 404, "无效的节点ID"),
    FILE_NOT_FOUND(1001, 404, "找不到文件"),
    FILE_TOO_LARGE(1002, 413, "文件过大"),
    RESOURCE_TYPE_NOT_MATCH(1003, 400, "文件资源无法覆盖目录资源（或相反）");

    int code;
    int status;
    String message;

    FileSystemError(int code, int status, String message) {
        this.code = code;
        this.status = status;
        this.message = message;
    }
}
