package com.sfc.archive.utils;

import com.sfc.archive.model.ArchiveFile;
import com.sfc.archive.model.ArchiveResource;
import com.xiaotao.saltedfishcloud.model.po.file.FileInfo;
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
import java.util.Optional;

/**
 * 新引擎模型与底层资源之间的转换工具。
 */
public final class EngineResourceUtils {
    private EngineResourceUtils() {
    }

    /**
     * 将一个网盘标准文件信息转为待压缩资源
     * @param fileInfo  网盘文件信息
     * @param basePath  该文件在压缩包内的所在目录路径
     * @param resource  文件数据资源，文件夹时可为 null
     */
    public static ArchiveResource toArchiveResource(FileInfo fileInfo, String basePath, Resource resource) {
        // 1. 先计算基础路径
        String archivePath = StringUtils.appendPath(basePath, fileInfo.getName());

        // 2. 补齐目录后缀
        if (fileInfo.isDir()) {
            archivePath += "/";
        }

        // 3. 处理冗余的前导斜杠
        if (archivePath.startsWith("/") && archivePath.length() > 1) {
            archivePath = archivePath.substring(1);
        }

        long now = System.currentTimeMillis();
        Long createdMillis = Optional.ofNullable(fileInfo.getCtime())
                .orElse(Optional.ofNullable(fileInfo.getCreateAt()).map(Date::getTime).orElse(now));
        Long lastModifiedMillis = Optional.ofNullable(fileInfo.getMtime())
                .orElse(Optional.ofNullable(fileInfo.getUpdateAt()).map(Date::getTime).orElse(now));

        return ArchiveResource.builder()
                .name(fileInfo.getName())
                .size(fileInfo.getSize())
                .archivePath(archivePath)
                .created(new Date(createdMillis))
                .lastModified(new Date(lastModifiedMillis))
                .resource(resource)
                .isDirectory(fileInfo.isDir())
                .build();
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
     * 归一化压缩包路径，遵循 {@link ArchiveResource#getArchivePath()} 语义。
     *
     * <p>约束：不使用前导 {@code /}，统一使用 {@code /} 作为分隔符。</p>
     *
     * @param archivePath 原始路径
     * @return 归一化路径
     */
    public static String normalizeArchivePath(String archivePath) {
        if (archivePath == null || archivePath.isEmpty()) {
            return archivePath;
        }
        String normalized = archivePath.replace('\\', '/');
        while (normalized.startsWith("/") && normalized.length() > 1) {
            normalized = normalized.substring(1);
        }
        return "/".equals(normalized) ? "" : normalized;
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
        boolean isSuccess = false;
        try {
            ResourceUtils.saveToFile(resource, tempPath);
            isSuccess = true;
            return new LocalFileResource(tempPath.toFile(), tempPath);
        } finally {
            if (!isSuccess) {
                Files.deleteIfExists(tempPath);
            }
        }
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

