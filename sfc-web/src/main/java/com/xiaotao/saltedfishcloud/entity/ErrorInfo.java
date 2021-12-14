package com.xiaotao.saltedfishcloud.entity;

import lombok.Getter;

/**
 * 错误信息<br>
 * <ul>
 *     <li>1xxx 基础文件系统错误</li>
 *     <li>2xxx 参数约束错误</li>
 *     <li>3xxx 文件收集错误</li>
 *     <li>4xxx 系统错误</li>
 * </ul>
 */
@Getter
public enum ErrorInfo {
    NODE_NOT_FOUND(1000, 404, "无效的节点ID"),
    FILE_NOT_FOUND(1001, 404, "找不到文件"),
    FILE_TOO_LARGE(1002, 413, "文件过大"),
    RESOURCE_TYPE_NOT_MATCH(1003, 400, "文件资源无法覆盖目录资源（或相反）"),
    FORMAT_ERROR(2001, 400, "格式错误"),
    INVALID_FILE_NAME(2002, 400, "无效的文件名"),
    COLLECTION_NOT_FOUND(3000, 404, "无效的收集任务ID"),
    COLLECTION_REQUIRE_LOGIN(3001, 401, "该收集任务要求登录"),
    COLLECTION_CHECK_FAILED(3002, 400, "不满足约束条件"),
    COLLECTION_CLOSED(3003, 406, "文件收集已关闭"),
    COLLECTION_FULL(3004, 406, "文件收集数已满"),
    COLLECTION_EXPIRED(3005, 400, "文件收集已过期"),
    SYSTEM_BUSY(4000, 500, "系统繁忙"),
    SYSTEM_FORBIDDEN(4001, 403, "权限不足"),
    SHARE_NOT_FOUND(5000, 404, "分享不存在"),
    SHARE_EXTRACT_ERROR(5001, 400, "提取码错误"),
    SHARE_EXPIRED(5002, 404, "分享已过期"),
    ARCHIVE_FORMAT_UNSUPPORTED(6000, 400, "未知或不支持的压缩包格式");
    int code;
    int status;
    String message;

    ErrorInfo(int code, int status, String message) {
        this.code = code;
        this.status = status;
        this.message = message;
    }
}
