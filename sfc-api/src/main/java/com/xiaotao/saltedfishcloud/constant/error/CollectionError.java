package com.xiaotao.saltedfishcloud.constant.error;

import lombok.Getter;

@Getter
public enum CollectionError implements ErrorInfo {
    // 文件收集模块
    COLLECTION_NOT_FOUND(3000, 404, "无效的收集任务ID"),
    COLLECTION_REQUIRE_LOGIN(3001, 401, "该收集任务要求登录"),
    COLLECTION_CHECK_FAILED(3002, 400, "不满足约束条件"),
    COLLECTION_CLOSED(3003, 406, "文件收集已关闭"),
    COLLECTION_FULL(3004, 406, "文件收集数已满"),
    COLLECTION_EXPIRED(3005, 400, "文件收集已过期");
    int code;
    int status;
    String message;

    CollectionError(int code, int status, String message) {
        this.code = code;
        this.status = status;
        this.message = message;
    }
}
