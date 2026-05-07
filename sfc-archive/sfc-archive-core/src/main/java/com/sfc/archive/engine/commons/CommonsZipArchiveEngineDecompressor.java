package com.sfc.archive.engine.commons;

import com.sfc.archive.engine.AbstractArchiveEngineDecompressor;
import com.sfc.archive.function.IOExceptionBiFunction;
import com.sfc.archive.model.ArchiveEngineProperty;
import com.sfc.archive.model.ArchiveResource;
import com.sfc.archive.utils.EngineResourceUtils;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.utils.PathUtils;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Date;
import java.util.Enumeration;
import java.util.Iterator;

/**
 * 基于 Apache Commons Compress 的 ZIP 解压执行器。
 */
public class CommonsZipArchiveEngineDecompressor extends AbstractArchiveEngineDecompressor {
    /**
     * 本地文件资源引用。
     */
    private final EngineResourceUtils.LocalFileResource localFileResource;

    /**
     * ZIP 文件对象。
     */
    private final ZipFile zipFile;

    /**
     * 创建 ZIP 解压器。
     *
     * @param resource 待解压资源
     * @param property 解压属性
     * @throws IOException 初始化失败
     */
    public CommonsZipArchiveEngineDecompressor(Resource resource, ArchiveEngineProperty property) throws IOException {
        this.localFileResource = EngineResourceUtils.toLocalFile(resource, ".zip");
        boolean isSuccess = false;
        try {
            this.zipFile = ZipFile.builder()
                    .setFile(localFileResource.getFile())
                    .setCharset(Charset.forName(property.getEncoding()))
                    .get();
            isSuccess = true;
        } finally {
            if (!isSuccess) {
                localFileResource.cleanup();
            }
        }
    }

    @Override
    public Iterator<ArchiveResource> getArchiveResources() {
        Enumeration<ZipArchiveEntry> entries = zipFile.getEntries();
        return new Iterator<>() {
            @Override
            public boolean hasNext() {
                return entries.hasMoreElements();
            }

            @Override
            public ArchiveResource next() {
                return toArchiveResource(entries.nextElement());
            }
        };
    }

    /**
     * 顺序遍历 ZIP 条目并逐个回调。
     *
     * @param func 解压回调
     * @throws IOException 解压失败
     */
    @Override
    public void decompressAll(IOExceptionBiFunction<InputStream, ArchiveResource, Boolean> func) throws IOException {
        requireDecompressFunction(func);
        Enumeration<ZipArchiveEntry> entries = zipFile.getEntries();
        while (entries.hasMoreElements()) {
            ZipArchiveEntry entry = entries.nextElement();
            ArchiveResource archiveResource = toArchiveResource(entry);
            if (entry.isDirectory()) {
                if (!continueDecompress(func, null, archiveResource)) {
                    return;
                }
                continue;
            }

            try (InputStream inputStream = wrapInputStreamWithCallback(
                    archiveResource.getArchivePath(),
                    entry.getSize(),
                    zipFile.getInputStream(entry)
            )) {
                if (!continueDecompress(func, inputStream, archiveResource)) {
                    return;
                }
            }
        }
    }

    @Override
    public InputStream getInputStream(String archivePath) throws IOException {
        String normalizedPath = normalizeEntryName(normalizeArchivePath(archivePath));
        ZipArchiveEntry entry = zipFile.getEntry(normalizedPath);
        if (entry == null || entry.isDirectory()) {
            throw new JsonException("压缩包内资源不存在: " + archivePath);
        }
        String callbackPath = EngineResourceUtils.normalizeArchivePath(entry.getName());
        return wrapInputStreamWithCallback(callbackPath, entry.getSize(), zipFile.getInputStream(entry));
    }

    @Override
    public void close() throws IOException {
        try {
            zipFile.close();
        } finally {
            localFileResource.cleanup();
        }
    }


    /**
     * 将 ZIP 条目转换为归档资源对象。
     *
     * @param entry ZIP 条目
     * @return 归档资源
     */
    private ArchiveResource toArchiveResource(ZipArchiveEntry entry) {
        String entryName = normalizeEntryName(entry.getName());
        return ArchiveResource.builder()
                .name(PathUtils.getLastNode(entryName))
                .size(entry.isDirectory() ? 0L : entry.getSize())
                .archivePath(EngineResourceUtils.normalizeArchivePath(entryName))
                .isDirectory(entry.isDirectory())
                .lastModified(toDate(entry.getTime()))
                .build();
    }


    /**
     * 统一 ZIP 条目路径分隔符。
     *
     * @param path 原始路径
     * @return 标准化路径
     */
    private String normalizeEntryName(String path) {
        return path == null ? null : path.replace('\\', '/');
    }

    /**
     * 将时间戳转换为日期对象。
     *
     * @param time 时间戳
     * @return 日期对象
     */
    private Date toDate(long time) {
        if (time <= 0) {
            return null;
        }
        return new Date(time);
    }
}


