package com.xiaotao.saltedfishcloud.service.file.impl.filesystem;

/**
 * 文件系统路由模式。
 */
enum FileSystemRouteMode {
    /**
     * 主文件系统路由。
     */
    MAIN,

    /**
     * 挂载存储，且文件记录由主文件系统维护。
     */
    MOUNT_WITH_MAIN_METADATA,

    /**
     * 挂载存储，且文件记录不由主文件系统维护。
     */
    MOUNT_WITH_DELEGATED_METADATA
}
