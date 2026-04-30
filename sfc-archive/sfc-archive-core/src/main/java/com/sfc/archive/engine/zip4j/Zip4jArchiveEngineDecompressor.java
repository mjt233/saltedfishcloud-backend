package com.sfc.archive.engine.zip4j;

import com.sfc.archive.engine.AbstractArchiveEngineDecompressor;
import com.sfc.archive.function.IOExceptionBiFunction;
import com.sfc.archive.utils.EngineResourceUtils;
import com.sfc.archive.model.ArchiveEngineProperty;
import com.sfc.archive.model.ArchiveResource;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.FileHeader;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.Iterator;

/**
 * zip4j 解压执行器。
 */
public class Zip4jArchiveEngineDecompressor extends AbstractArchiveEngineDecompressor {
    /**
     * 本地文件引用。
     */
    private final EngineResourceUtils.LocalFileResource localFileResource;

    /**
     * zip4j 文件对象。
     */
    private final ZipFile zipFile;

    /**
     * 创建 zip4j 解压器。
     *
     * @param resource 待解压资源
     * @param property 属性
     * @throws IOException 初始化失败
     */
    public Zip4jArchiveEngineDecompressor(Resource resource, ArchiveEngineProperty property) throws IOException {
        this.localFileResource = EngineResourceUtils.toLocalFile(resource, ".zip");
        String password = property.getEncryptionParam() == null ? null : property.getEncryptionParam().getPassword();
        this.zipFile = password == null
                ? new ZipFile(localFileResource.getFile())
                : new ZipFile(localFileResource.getFile(), password.toCharArray());
    }

    @Override
    public Iterator<ArchiveResource> getArchiveResources() throws IOException {
        Iterator<FileHeader> headerIterator = zipFile.getFileHeaders().iterator();
        return new Iterator<>() {
            @Override
            public boolean hasNext() {
                return headerIterator.hasNext();
            }

            @Override
            public ArchiveResource next() {
                return toArchiveResource(headerIterator.next());
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
        for (FileHeader header : zipFile.getFileHeaders()) {
            ArchiveResource archiveResource = toArchiveResource(header);
            if (header.isDirectory()) {
                if (!continueDecompress(func, null, archiveResource)) {
                    return;
                }
                continue;
            }

            try (InputStream inputStream = wrapInputStreamWithCallback(
                    archiveResource.getArchivePath(),
                    header.getUncompressedSize(),
                    zipFile.getInputStream(header)
            )) {
                if (!continueDecompress(func, inputStream, archiveResource)) {
                    return;
                }
            }
        }
    }

    @Override
    public InputStream getInputStream(String archivePath) throws IOException {
        FileHeader fileHeader = zipFile.getFileHeader(normalizeArchivePath(archivePath));
        if (fileHeader == null || fileHeader.isDirectory()) {
            throw new JsonException("压缩包内资源不存在: " + archivePath);
        }
        String normalizedArchivePath = EngineResourceUtils.normalizeArchivePath(fileHeader.getFileName());
        return wrapInputStreamWithCallback(normalizedArchivePath, fileHeader.getUncompressedSize(), zipFile.getInputStream(fileHeader));
    }

    @Override
    public void close() throws IOException {
        localFileResource.cleanup();
    }


    /**
     * 将 ZIP 条目转换为归档资源对象。
     *
     * @param header ZIP 条目
     * @return 归档资源
     */
    private ArchiveResource toArchiveResource(FileHeader header) {
        String fileName = header.getFileName();
        return ArchiveResource.builder()
                .name(extractName(fileName))
                .size(header.isDirectory() ? 0L : header.getUncompressedSize())
                .archivePath(EngineResourceUtils.normalizeArchivePath(fileName))
                .isDirectory(header.isDirectory())
                .lastModified(header.getLastModifiedTime() > 0 ? new Date(header.getLastModifiedTimeEpoch()) : null)
                .build();
    }


    /**
     * 从路径提取文件名。
     *
     * @param path 路径
     * @return 文件名
     */
    private String extractName(String path) {
        int slashIndex = path.lastIndexOf('/');
        if (slashIndex < 0 || slashIndex == path.length() - 1) {
            return path;
        }
        return path.substring(slashIndex + 1);
    }
}


