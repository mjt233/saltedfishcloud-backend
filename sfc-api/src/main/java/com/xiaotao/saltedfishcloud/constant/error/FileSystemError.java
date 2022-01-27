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
    TARGET_IS_SUB_DIR(1006, 400, "目标位置不能是子目录");

    private final int code;
    private final int status;
    private final String message;

}
