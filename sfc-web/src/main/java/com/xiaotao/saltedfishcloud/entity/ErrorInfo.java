package com.xiaotao.saltedfishcloud.entity;

import lombok.Getter;

/**
 * 错误信息
 * @TODO 抽象出接口，让枚举类实现，解决报错信息配置过于集中
 */
@Getter
public enum ErrorInfo {

    // 文件服务
    NODE_NOT_FOUND(1000, 404, "无效的节点ID"),
    FILE_NOT_FOUND(1001, 404, "找不到文件"),
    FILE_TOO_LARGE(1002, 413, "文件过大"),
    RESOURCE_TYPE_NOT_MATCH(1003, 400, "文件资源无法覆盖目录资源（或相反）"),

    // 通用模块
    FORMAT_ERROR(2001, 400, "格式错误"),
    INVALID_FILE_NAME(2002, 400, "无效的文件名"),

    // 文件收集模块
    COLLECTION_NOT_FOUND(3000, 404, "无效的收集任务ID"),
    COLLECTION_REQUIRE_LOGIN(3001, 401, "该收集任务要求登录"),
    COLLECTION_CHECK_FAILED(3002, 400, "不满足约束条件"),
    COLLECTION_CLOSED(3003, 406, "文件收集已关闭"),
    COLLECTION_FULL(3004, 406, "文件收集数已满"),
    COLLECTION_EXPIRED(3005, 400, "文件收集已过期"),

    // 系统模块
    SYSTEM_BUSY(4000, 500, "系统繁忙"),
    SYSTEM_FORBIDDEN(4001, 403, "权限不足"),
    SYSTEM_ERROR(4002, 500, "系统错误"),

    // 分享模块
    SHARE_NOT_FOUND(5000, 404, "分享不存在"),
    SHARE_EXTRACT_ERROR(5001, 400, "提取码错误"),
    SHARE_EXPIRED(5002, 404, "分享已过期"),

    // 压缩模块
    ARCHIVE_FORMAT_UNSUPPORTED(6000, 400, "未知或不支持的压缩包格式"),

    // 账号系统
    EMAIL_EXIST(10000, 400, "邮箱已被使用"),
    USER_EXIST(10001, 400, "用户已注册"),
    EMAIL_REG_DISABLE(10002, 400, "不允许邮件注册"),
    REG_CODE_DISABLE(10003, 400, "不允许邀请码注册"),
    REG_CODE_ERROR(10004, 400, "邀请注册码错误"),
    EMAIL_CODE_ERROR(10005, 400, "邮箱验证码错误"),
    USER_NOT_EXIST(10006, 404, "用户不存在"),
    EMAIL_NOT_SET(10007, 404, "用户未设置邮箱");

    int code;
    int status;
    String message;

    ErrorInfo(int code, int status, String message) {
        this.code = code;
        this.status = status;
        this.message = message;
    }
}
