package com.xiaotao.saltedfishcloud.entity;

import lombok.Getter;

@Getter
public enum ErrorInfo {
    NODE_NOT_FOUND(1000, 404, "无效的节点ID"),
    FILE_NOT_FOUND(1001, 404, "找不到文件"),
    COLLECTION_NOT_FOUND(3000, 404, "无效的收集任务ID"),
    COLLECTION_REQUIRE_LOGIN(3001, 401, "该收集任务要求登录");
    int code;
    int status;
    String message;

    ErrorInfo(int code, int status, String message) {
        this.code = code;
        this.status = status;
        this.message = message;
    }
}
