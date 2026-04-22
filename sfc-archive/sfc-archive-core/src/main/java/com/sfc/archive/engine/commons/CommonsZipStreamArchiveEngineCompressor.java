package com.sfc.archive.engine.commons;

import com.sfc.archive.ArchiveEngineCompressor;
import com.sfc.archive.model.ArchiveProperty;
import com.sfc.archive.model.ArchiveResource;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.compress.archivers.zip.ZipMethod;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.attribute.FileTime;

/**
 * 基于 ZipArchiveOutputStream 的流式压缩器。
 */
public class CommonsZipStreamArchiveEngineCompressor implements ArchiveEngineCompressor {
    /**
     * ZIP 输出流。
     */
    private final ZipArchiveOutputStream outputStream;

    /**
     * 创建流式压缩器。
     *
     * @param targetOutput 目标输出流
     * @param property     压缩属性
     */
    public CommonsZipStreamArchiveEngineCompressor(OutputStream targetOutput, ArchiveProperty property) {
        this.outputStream = new ZipArchiveOutputStream(targetOutput);
        this.outputStream.setEncoding(property.getEncoding());
    }

    @Override
    public void addArchiveResource(ArchiveResource resource) throws IOException {
        if (resource == null) {
            throw new JsonException("archive resource 不能为空");
        }
        if (Boolean.FALSE.equals(resource.getIsDirectory()) && resource.getResource() == null) {
            throw new JsonException("文件资源 resource 不能为空");
        }

        String normalizedPath = normalizePath(resource.getArchivePath(), Boolean.TRUE.equals(resource.getIsDirectory()));
        ZipArchiveEntry entry = new ZipArchiveEntry(normalizedPath);

        if (Boolean.TRUE.equals(resource.getIsDirectory())) {
            entry.setMethod(ZipMethod.STORED.getCode());
            entry.setSize(0);
            entry.setCompressedSize(0);
            entry.setCrc(0);
        } else if (resource.getSize() != null && resource.getSize() >= 0) {
            entry.setSize(resource.getSize());
        }

        if (resource.getLastModified() != null) {
            entry.setLastModifiedTime(FileTime.fromMillis(resource.getLastModified().getTime()));
        }
        if (resource.getCreated() != null) {
            entry.setCreationTime(FileTime.fromMillis(resource.getCreated().getTime()));
        }

        outputStream.putArchiveEntry(entry);
        if (Boolean.FALSE.equals(resource.getIsDirectory())) {
            try (InputStream inputStream = resource.getResource().getInputStream()) {
                StreamUtils.copy(inputStream, outputStream);
            }
        }
        outputStream.closeArchiveEntry();
    }

    @Override
    public void close() throws IOException {
        outputStream.finish();
        outputStream.close();
    }

    /**
     * 归一化路径并处理目录结尾。
     *
     * @param archivePath 资源路径
     * @param directory   是否目录
     * @return ZIP entry 路径
     */
    private String normalizePath(String archivePath, boolean directory) {
        if (archivePath == null || archivePath.isEmpty()) {
            throw new JsonException("archivePath 不能为空");
        }
        String normalized = archivePath.startsWith("/") ? archivePath.substring(1) : archivePath;
        if (directory && !normalized.endsWith("/")) {
            normalized = normalized + "/";
        }
        return normalized;
    }
}


