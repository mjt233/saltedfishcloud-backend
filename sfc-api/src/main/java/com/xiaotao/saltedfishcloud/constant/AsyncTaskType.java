package com.xiaotao.saltedfishcloud.constant;

/**
 * 系统内置的异步事件类型
 */
public interface AsyncTaskType {
    /**
     * 离线下载异步任务
     */
    String OFFLINE_DOWNLOAD = "offline-download";

    /**
     * 文件压缩
     */
    String ARCHIVE_COMPRESS = "archive-compress";

    /**
     * 文件解压缩
     */
    String ARCHIVE_EXTRACTOR = "archive-extractor";

    /**
     * 文件复制
     */
    String FILE_COPY = "file-copy";
}
