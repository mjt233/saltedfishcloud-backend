package com.sfc.archive.engine.commons;

import com.sfc.archive.engine.AbstractArchiveEngineDecompressor;
import com.sfc.archive.function.IOExceptionBiFunction;
import com.sfc.archive.model.ArchiveEngineProperty;
import com.sfc.archive.model.ArchiveResource;
import com.sfc.archive.utils.EngineResourceUtils;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.utils.PathUtils;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarFile;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * TAR 及 TAR 复合格式（tar.gz / tar.xz / tar.bz2）解压执行器。
 */
public class CommonsTarArchiveEngineDecompressor extends AbstractArchiveEngineDecompressor {
    /**
     * 原始本地文件引用（仅资源转换为临时文件时使用）。
     */
    private final EngineResourceUtils.LocalFileResource localFileResource;

    /**
     * TAR 文件对象。
     */
    private final TarFile tarFile;

    /**
     * 临时 TAR 文件路径（仅复合格式使用）。
     */
    private final Path tempTarPath;

    /**
     * 路径到条目的惰性索引，仅在 {@link #getInputStream(String)} 首次调用时构建。
     */
    private volatile Map<String, TarArchiveEntry> entryMap = null;

    /**
     * 创建 TAR 解压器。
     *
     * @param resource  待解压资源
     * @param property  解压属性
     * @param extension 当前格式扩展名
     * @throws IOException 初始化失败
     */
    public CommonsTarArchiveEngineDecompressor(Resource resource,
                                               ArchiveEngineProperty property,
                                               String extension) throws IOException {
        TarFileHolder holder = openTarFile(resource, property, extension);
        this.localFileResource = holder.localFileResource;
        this.tempTarPath = holder.tempTarPath;
        this.tarFile = holder.tarFile;
    }

    /**
     * 惰性获取路径到条目的索引，首次调用时才构建。
     *
     * @return 路径到条目的映射
     */
    private Map<String, TarArchiveEntry> getEntryMap() {
        if (entryMap == null) {
            synchronized (this) {
                if (entryMap == null) {
                    Map<String, TarArchiveEntry> map = new LinkedHashMap<>();
                    tarFile.getEntries().forEach(entry -> map.put(normalizeEntryName(entry.getName()), entry));
                    entryMap = map;
                }
            }
        }
        return entryMap;
    }

    @Override
    public Iterator<ArchiveResource> getArchiveResources() {
        Iterator<TarArchiveEntry> iterator = tarFile.getEntries().iterator();
        return new Iterator<>() {
            @Override
            public boolean hasNext() {
                return iterator.hasNext();
            }

            @Override
            public ArchiveResource next() {
                return toArchiveResource(iterator.next());
            }
        };
    }

    @Override
    public void decompressAll(IOExceptionBiFunction<InputStream, ArchiveResource, Boolean> func) throws IOException {
        requireDecompressFunction(func);
        for (TarArchiveEntry entry : tarFile.getEntries()) {
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
                    tarFile.getInputStream(entry)
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
        TarArchiveEntry entry = getEntryMap().get(normalizedPath);
        if (entry == null || entry.isDirectory()) {
            throw new JsonException("压缩包内资源不存在: " + archivePath);
        }
        return wrapInputStreamWithCallback(
                EngineResourceUtils.normalizeArchivePath(entry.getName()),
                entry.getSize(),
                tarFile.getInputStream(entry)
        );
    }

    @Override
    public void close() throws IOException {
        try {
            tarFile.close();
        } finally {
            cleanupTempFile();
            cleanupLocalResource();
        }
    }

    /**
     * 打开 TAR 文件对象。
     *
     * @param resource  待解压资源
     * @param property  解压属性
     * @param extension 扩展名
     * @return TAR 文件持有对象
     * @throws IOException 初始化失败
     */
    private TarFileHolder openTarFile(Resource resource, ArchiveEngineProperty property, String extension) throws IOException {
        if (".tar".equals(extension)) {
            EngineResourceUtils.LocalFileResource localResource = EngineResourceUtils.toLocalFile(resource, ".tar");
            TarFile file = new TarFile(localResource.getFile(), property.getEncoding());
            return new TarFileHolder(file, localResource, null);
        }

        if (".tar.gz".equals(extension) || ".tar.xz".equals(extension) || ".tar.bz2".equals(extension)) {
            Path extractedTarPath = Files.createTempFile("sfc-tar-", ".tar");
            boolean success = false;
            try {
                try (InputStream sourceInputStream = resource.getInputStream();
                     InputStream compressorInputStream = createCompressorInputStream(sourceInputStream, extension);
                     OutputStream tempOutputStream = Files.newOutputStream(extractedTarPath)) {
                    compressorInputStream.transferTo(tempOutputStream);
                }
                TarFile file = new TarFile(extractedTarPath.toFile(), property.getEncoding());
                success = true;
                return new TarFileHolder(file, null, extractedTarPath);
            } finally {
                if (!success) {
                    Files.deleteIfExists(extractedTarPath);
                }
            }
        }

        throw new JsonException("不支持的 TAR 解压格式: " + extension);
    }

    /**
     * 按扩展名创建压缩流输入对象。
     *
     * @param sourceInputStream 原始输入流
     * @param extension         扩展名
     * @return 解压后的输入流
     * @throws IOException 初始化失败
     */
    private InputStream createCompressorInputStream(InputStream sourceInputStream, String extension) throws IOException {
        if (".tar.gz".equals(extension)) {
            return new GzipCompressorInputStream(sourceInputStream);
        }
        if (".tar.xz".equals(extension)) {
            return new XZCompressorInputStream(sourceInputStream);
        }
        if (".tar.bz2".equals(extension)) {
            return new BZip2CompressorInputStream(sourceInputStream);
        }
        throw new JsonException("不支持的 TAR 复合格式: " + extension);
    }

    /**
     * 将 TAR 条目映射为归档资源。
     *
     * @param entry TAR 条目
     * @return 归档资源
     */
    private ArchiveResource toArchiveResource(TarArchiveEntry entry) {
        String normalizedPath = EngineResourceUtils.normalizeArchivePath(entry.getName());
        return ArchiveResource.builder()
                .name(PathUtils.getLastNode(normalizedPath))
                .size(entry.isDirectory() ? 0L : entry.getSize())
                .archivePath(normalizedPath)
                .isDirectory(entry.isDirectory())
                .lastModified(entry.getLastModifiedDate() == null ? null : new Date(entry.getLastModifiedDate().getTime()))
                .build();
    }

    /**
     * 标准化 TAR 条目路径。
     *
     * @param entryName 原始路径
     * @return 标准路径
     */
    private String normalizeEntryName(String entryName) {
        return normalizeArchivePath(entryName).replace('\\', '/');
    }

    /**
     * 释放复合格式生成的临时 TAR 文件。
     */
    private void cleanupTempFile() {
        if (tempTarPath == null) {
            return;
        }
        try {
            Files.deleteIfExists(tempTarPath);
        } catch (IOException ignore) {
        }
    }

    /**
     * 释放原始本地文件临时资源。
     */
    private void cleanupLocalResource() {
        if (localFileResource == null) {
            return;
        }
        localFileResource.cleanup();
    }

    /**
     * TAR 文件持有对象。
     */
    private static class TarFileHolder {
        /**
         * TAR 文件对象。
         */
        private final TarFile tarFile;

        /**
         * 本地文件资源。
         */
        private final EngineResourceUtils.LocalFileResource localFileResource;

        /**
         * 临时 TAR 路径。
         */
        private final Path tempTarPath;

        /**
         * 创建 TAR 文件持有对象。
         *
         * @param tarFile           TAR 文件对象
         * @param localFileResource 本地文件资源
         * @param tempTarPath       临时 TAR 路径
         */
        private TarFileHolder(TarFile tarFile,
                              EngineResourceUtils.LocalFileResource localFileResource,
                              Path tempTarPath) {
            this.tarFile = tarFile;
            this.localFileResource = localFileResource;
            this.tempTarPath = tempTarPath;
        }
    }
}

