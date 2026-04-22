package com.sfc.archive.engine.support;

import com.sfc.archive.model.ArchiveFile;
import com.sfc.archive.model.ArchiveResource;
import com.xiaotao.saltedfishcloud.utils.PathUtils;
import com.xiaotao.saltedfishcloud.utils.ResourceUtils;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;

/**
 * 新引擎模型与底层资源之间的转换工具。
 */
public final class EngineResourceUtils {
    private EngineResourceUtils() {
    }

    /**
     * 将旧版 ArchiveFile 映射为 ArchiveResource。
     *
     * @param archiveFile 旧版文件信息
     * @return 新版资源信息
     */
    public static ArchiveResource toArchiveResource(ArchiveFile archiveFile) {
        return ArchiveResource.builder()
                .name(archiveFile.getName())
                .size(archiveFile.getSize())
                .archivePath(normalizeArchivePath(archiveFile.getPath()))
                .isDirectory(archiveFile.isDirectory())
                .lastModified(archiveFile.getMtime() == null ? null : new Date(archiveFile.getMtime()))
                .created(archiveFile.getCtime() == null ? null : new Date(archiveFile.getCtime()))
                .build();
    }

    /**
     * 归一化压缩包路径，统一以 '/' 开头。
     *
     * @param archivePath 原始路径
     * @return 归一化路径
     */
    public static String normalizeArchivePath(String archivePath) {
        if (archivePath == null || archivePath.isEmpty()) {
            return "/";
        }
        return archivePath.startsWith("/") ? archivePath : "/" + archivePath;
    }

    /**
     * 将 Resource 转换为本地文件，必要时写入临时文件。
     *
     * @param resource 资源对象
     * @param suffix   临时文件后缀
     * @return 文件结果
     * @throws IOException 读取/写入失败
     */
    public static LocalFileResource toLocalFile(Resource resource, String suffix) throws IOException {
        if (resource.isFile() || resource instanceof PathResource) {
            return new LocalFileResource(resource.getFile(), null);
        }
        Path tempPath = Paths.get(StringUtils.appendPath(PathUtils.getTempDirectory(), StringUtils.getRandomString(10) + suffix));
        ResourceUtils.saveToFile(resource, tempPath);
        return new LocalFileResource(tempPath.toFile(), tempPath);
    }

    /**
     * 本地文件结果对象。
     */
    public static final class LocalFileResource {
        /**
         * 本地文件。
         */
        private final File file;

        /**
         * 临时文件路径，非临时文件时为 null。
         */
        private final Path tempPath;

        /**
         * 创建本地文件结果。
         *
         * @param file     本地文件
         * @param tempPath 临时文件路径
         */
        public LocalFileResource(File file, Path tempPath) {
            this.file = file;
            this.tempPath = tempPath;
        }

        /**
         * 获取文件。
         *
         * @return 文件对象
         */
        public File getFile() {
            return file;
        }

        /**
         * 释放临时文件。
         */
        public void cleanup() {
            if (tempPath == null) {
                return;
            }
            try {
                Files.deleteIfExists(tempPath);
            } catch (IOException ignore) {
            }
        }
    }
}

