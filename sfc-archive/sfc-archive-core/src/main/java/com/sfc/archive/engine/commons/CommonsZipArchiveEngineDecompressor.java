package com.sfc.archive.engine.commons;

import com.sfc.archive.ArchiveEngineDecompressor;
import com.sfc.archive.engine.support.EngineResourceUtils;
import com.sfc.archive.model.ArchiveProperty;
import com.sfc.archive.model.ArchiveResource;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.utils.PathUtils;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;

/**
 * 基于 Apache Commons Compress 的 ZIP 解压执行器。
 */
public class CommonsZipArchiveEngineDecompressor implements ArchiveEngineDecompressor {
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
    public CommonsZipArchiveEngineDecompressor(Resource resource, ArchiveProperty property) throws IOException {
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
        List<ArchiveResource> resources = new ArrayList<>();
        while (entries.hasMoreElements()) {
            ZipArchiveEntry entry = entries.nextElement();
            String entryName = normalizeEntryName(entry.getName());
            resources.add(ArchiveResource.builder()
                    .name(PathUtils.getLastNode(entryName))
                    .size(entry.isDirectory() ? 0L : entry.getSize())
                    .archivePath(EngineResourceUtils.normalizeArchivePath(entryName))
                    .isDirectory(entry.isDirectory())
                    .lastModified(toDate(entry.getTime()))
                    .build());
        }
        return resources.iterator();
    }

    @Override
    public InputStream getInputStream(String archivePath) throws IOException {
        String normalizedPath = normalizeEntryName(stripPrefixSlash(archivePath));
        ZipArchiveEntry entry = zipFile.getEntry(normalizedPath);
        if (entry == null || entry.isDirectory()) {
            throw new JsonException("压缩包内资源不存在: " + archivePath);
        }
        return zipFile.getInputStream(entry);
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
     * 去掉路径前导斜杠。
     *
     * @param path 压缩包路径
     * @return 去掉前导斜杠后的路径
     */
    private String stripPrefixSlash(String path) {
        if (path == null || path.isEmpty()) {
            throw new JsonException("archivePath 不能为空");
        }
        return path.startsWith("/") ? path.substring(1) : path;
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


