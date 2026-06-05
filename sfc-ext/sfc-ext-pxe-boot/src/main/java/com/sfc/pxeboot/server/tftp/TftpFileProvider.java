package com.sfc.pxeboot.server.tftp;

import com.xiaotao.saltedfishcloud.service.file.DiskFileSystemManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;

import java.io.InputStream;

/**
 * TFTP 文件提供器，负责从网盘文件系统中打开文件流。
 */
@Slf4j
public class TftpFileProvider {

    private final DiskFileSystemManager diskFileSystemManager;

    /**
     * 构造文件提供器。
     *
     * @param diskFileSystemManager 网盘文件系统管理器
     */
    public TftpFileProvider(DiskFileSystemManager diskFileSystemManager) {
        this.diskFileSystemManager = diskFileSystemManager;
    }

    /**
     * 打开网盘文件输入流，用于流式传输。
     *
     * @param filename 文件路径（TFTP 客户端请求的路径）
     * @return 文件输入流，若文件不存在或打开失败返回 null
     */
    public InputStream openFileStream(String filename) {
        try {
            String normalizedPath = filename.replace("\\", "/");
            if (normalizedPath.startsWith("/")) {
                normalizedPath = normalizedPath.substring(1);
            }

            int lastSlash = normalizedPath.lastIndexOf('/');
            String dirPath;
            String fileName;
            if (lastSlash >= 0) {
                dirPath = "/" + normalizedPath.substring(0, lastSlash);
                fileName = normalizedPath.substring(lastSlash + 1);
            } else {
                dirPath = "/";
                fileName = normalizedPath;
            }

            Resource resource = diskFileSystemManager.getMainFileSystem().getResource(0L, dirPath, fileName);
            if (resource == null) {
                return null;
            }

            return resource.getInputStream();
        } catch (Exception e) {
            log.error("{} 打开文件流失败: {}", TftpConstants.LOG_PREFIX, filename, e);
            return null;
        }
    }
}
