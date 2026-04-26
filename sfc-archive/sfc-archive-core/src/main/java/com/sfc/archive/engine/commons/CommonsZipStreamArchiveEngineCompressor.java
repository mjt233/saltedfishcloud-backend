package com.sfc.archive.engine.commons;

import com.sfc.archive.engine.AbstractArchiveEngineCompressor;
import com.sfc.archive.model.ArchiveProperty;
import com.sfc.archive.model.ArchiveResource;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.compress.archivers.zip.ZipMethod;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.attribute.FileTime;
import java.util.zip.ZipEntry;

/**
 * 基于 ZipArchiveOutputStream 的流式压缩器。
 */
public class CommonsZipStreamArchiveEngineCompressor extends AbstractArchiveEngineCompressor {
    /**
     * ZIP 输出流。
     */
    private final ZipArchiveOutputStream zipOutputStream;

    /**
     * 创建流式压缩器。
     *
     * @param targetOutput 目标输出流
     * @param property     压缩属性
     */
    public CommonsZipStreamArchiveEngineCompressor(OutputStream targetOutput, ArchiveProperty property) {
        super(property);
        this.zipOutputStream = new ZipArchiveOutputStream(targetOutput);
        this.zipOutputStream.setEncoding(property.getEncoding());
    }

    /**
     * 为当前资源创建 ZIP entry 并返回可写输出流。
     *
     * @param resource       资源信息
     * @return 文件资源返回 ZIP 输出流，目录返回 null
     * @throws IOException 创建 entry 失败
     */
    @Override
    protected OutputStream openEntryOutputStream(ArchiveResource resource) throws IOException {
        ZipArchiveEntry entry = new ZipArchiveEntry(resource.getArchivePath());

        if (Boolean.TRUE.equals(resource.getIsDirectory())) {
            entry.setMethod(ZipEntry.STORED);
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

        zipOutputStream.putArchiveEntry(entry);
        if (Boolean.TRUE.equals(resource.getIsDirectory())) {
            return null;
        }
        return zipOutputStream;
    }

    /**
     * 关闭当前 ZIP entry。
     *
     * @throws IOException 关闭失败
     */
    @Override
    protected void doCloseCurrentEntry() throws IOException {
        zipOutputStream.closeArchiveEntry();
    }

    @Override
    public void close() throws IOException {
        zipOutputStream.finish();
        zipOutputStream.close();
    }
}


