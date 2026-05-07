package com.xiaotao.saltedfishcloud.enums;

import com.xiaotao.saltedfishcloud.constant.error.ErrorInfo;
import lombok.Getter;

@Getter
public enum ArchiveError implements ErrorInfo {
    ARCHIVE_EMPTY_DIRECTORY(6001, 400, "选定的目录不存在文件"),
    ARCHIVE_FORMAT_UNSUPPORTED(6000, 400, "未知或不支持的压缩包格式"),
    ARCHIVE_LIST_RESOURCE_NOT_SUPPORTED(6002, 400, "该资源不支持预览文件列表"),
    ARCHIVE_LIST_RESOURCE_TOO_MANY(6003, 400, "压缩包文件数量超过接口上限");

    int code;
    int status;
    String message;

    ArchiveError(int code, int status, String message) {
        this.code = code;
        this.status = status;
        this.message = message;
    }
}
