package com.xiaotao.saltedfishcloud.constant.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum FileSystemError implements ErrorInfo {
    NODE_NOT_FOUND(1000, 404, "无效的节点ID"),
    FILE_NOT_FOUND(1001, 404, "找不到文件"),
    FILE_TOO_LARGE(1002, 413, "文件过大"),
    RESOURCE_TYPE_NOT_MATCH(1003, 400, "文件资源无法覆盖目录资源（或相反）"),
    QUICK_SAVE_NOT_HIT(1004, 201, "未命中，请上传"),
    DIR_TOO_DEPTH(1005, 400, "目录深度过大"),
    TARGET_IS_SUB_DIR(1006, 400, "目标位置不能是子目录"),
    MOUNT_POINT_EXIST(1007, 400, "已存在同名挂载点"),
    FILE_EXIST(1008, 400, "已存在同名文件"),
    NOT_ALLOW_FILE_OVERWRITE_DIR(1009, 400, "不能用文件覆盖目录"),
    NOT_ALLOW_DIR_OVERWRITE_FILE(1009, 400, "不能用目录覆盖文件"),
    INVALID_PATH(1010, 400, "无效的文件路径，不得包含'?<>:*\"'、回车/换行符、'/../' 或使用 '/..' 结尾"),
    INVALID_FILE_NAME(1011, 400, "无效的文件路径，不得包含'?<>:*\"'、回车/换行符、/或\\");

    private final int code;
    private final int status;
    private final String message;

}
