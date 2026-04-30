package com.sfc.archive.engine.commons;

import com.sfc.archive.engine.AbstractArchiveEngineDecompressor;
import com.sfc.archive.function.IOExceptionBiFunction;
import com.sfc.archive.model.ArchiveResource;
import com.sfc.archive.utils.EngineResourceUtils;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.utils.PathUtils;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * 单流压缩格式（.gz/.xz/.bz2）解压执行器。
 */
public class CommonsCompressorStreamArchiveEngineDecompressor extends AbstractArchiveEngineDecompressor {
    /**
     * 待解压资源。
     */
    private final Resource resource;

    /**
     * 当前压缩格式扩展名。
     */
    private final String extension;

    /**
     * 压缩包内唯一资源信息。
     */
    private final ArchiveResource archiveResource;

    /**
     * 创建单流格式解压执行器。
     *
     * @param resource  待解压资源
     * @param extension 扩展名
     */
    public CommonsCompressorStreamArchiveEngineDecompressor(Resource resource, String extension) {
        this.resource = resource;
        this.extension = extension;
        String archivePath = EngineResourceUtils.normalizeArchivePath(resolveEntryName(resource.getFilename(), extension));
        this.archiveResource = ArchiveResource.builder()
                .name(PathUtils.getLastNode(archivePath))
                .size(null)
                .archivePath(archivePath)
                .isDirectory(false)
                .build();
    }

    @Override
    public Iterator<ArchiveResource> getArchiveResources() {
        return new Iterator<>() {
            /**
             * 标记唯一条目是否已返回。
             */
            private boolean consumed;

            @Override
            public boolean hasNext() {
                return !consumed;
            }

            @Override
            public ArchiveResource next() {
                if (consumed) {
                    throw new NoSuchElementException("没有更多压缩包资源");
                }
                consumed = true;
                return archiveResource;
            }
        };
    }

    @Override
    public void decompressAll(IOExceptionBiFunction<InputStream, ArchiveResource, Boolean> func) throws IOException {
        requireDecompressFunction(func);
        try (InputStream inputStream = getInputStream(archiveResource.getArchivePath())) {
            continueDecompress(func, inputStream, archiveResource);
        }
    }

    @Override
    public InputStream getInputStream(String archivePath) throws IOException {
        String normalizedPath = normalizeArchivePath(archivePath);
        String currentPath = normalizeArchivePath(archiveResource.getArchivePath());
        if (!currentPath.equals(normalizedPath)) {
            throw new JsonException("压缩包内资源不存在: " + archivePath);
        }
        InputStream sourceInputStream = resource.getInputStream();
        InputStream stream = null;
        boolean success = false;
        try {
            stream = createCompressorInputStream(sourceInputStream, extension);
            InputStream callbackInputStream = wrapInputStreamWithCallback(archiveResource.getArchivePath(), -1, stream);
            success = true;
            return callbackInputStream;
        } finally {
            if (!success) {
                closeQuietly(stream);
                closeQuietly(sourceInputStream);
            }
        }
    }

    @Override
    public void close() {
    }

    /**
     * 按扩展名创建解压输入流。
     *
     * @param sourceInputStream 原始输入流
     * @param extension         扩展名
     * @return 解压输入流
     * @throws IOException 初始化失败
     */
    private InputStream createCompressorInputStream(InputStream sourceInputStream, String extension) throws IOException {
        if (".gz".equals(extension)) {
            return new GzipCompressorInputStream(sourceInputStream);
        }
        if (".xz".equals(extension)) {
            return new XZCompressorInputStream(sourceInputStream);
        }
        if (".bz2".equals(extension)) {
            return new BZip2CompressorInputStream(sourceInputStream);
        }
        throw new JsonException("不支持的单流解压格式: " + extension);
    }

    /**
     * 根据压缩包文件名推导解压后的文件名。
     *
     * @param filename  压缩包文件名
     * @param extension 扩展名
     * @return 解压后资源名
     */
    private String resolveEntryName(String filename, String extension) {
        if (filename == null || filename.isEmpty()) {
            return "content";
        }
        String lowerCaseFilename = filename.toLowerCase();
        if (lowerCaseFilename.endsWith(extension)) {
            String rawName = filename.substring(0, filename.length() - extension.length());
            if (!rawName.isEmpty()) {
                return rawName;
            }
        }
        return filename + ".out";
    }

    /**
     * 关闭资源并忽略异常。
     *
     * @param closeable 可关闭对象
     */
    private void closeQuietly(AutoCloseable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (Exception ignore) {
        }
    }
}

