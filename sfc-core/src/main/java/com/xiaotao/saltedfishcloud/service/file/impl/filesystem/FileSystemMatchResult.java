package com.xiaotao.saltedfishcloud.service.file.impl.filesystem;

import com.xiaotao.saltedfishcloud.model.po.MountPoint;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystem;
import com.xiaotao.saltedfishcloud.utils.StringUtils;

/**
 * 文件系统匹配结果
 */
class FileSystemMatchResult {
    /**
     * 匹配到的文件系统
     */
    DiskFileSystem fileSystem;

    /**
     * 在对应的文件系统上解析后的对应路径（原始请求路径）
     */
    String resolvedPath;

    /**
     * 匹配到的挂载点，若没匹配到则为null
     */
    MountPoint mountPoint;

    public FileSystemMatchResult(DiskFileSystem fileSystem, MountPoint mountPoint, String resolvedPath) {
        this.fileSystem = fileSystem;
        this.mountPoint = mountPoint;
        this.resolvedPath = resolvedPath;
    }

    @Override
    public String toString() {
        return "FileSystemMatchResult{" +
                "fileSystem=" + fileSystem +
                ", resolvedPath='" + resolvedPath + '\'' +
                '}';
    }

    /**
     * 判断请求的路径是否为匹配的挂载点本身
     */
    public boolean isMountPath(String path) {
        if (mountPoint == null) {
            return false;
        }
        return StringUtils.isPathEqual(path, mountPoint.getPath());
    }

    /**
     * 判断是否为一个启用了代理文件存储记录的挂载点
     */
    public boolean isProxyStoreRecordMountPoint() {
        return mountPoint != null && Boolean.TRUE.equals(mountPoint.getIsProxyStoreRecord());
    }
}
