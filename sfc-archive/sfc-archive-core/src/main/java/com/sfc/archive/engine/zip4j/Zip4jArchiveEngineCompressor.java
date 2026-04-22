package com.sfc.archive.engine.zip4j;

import com.sfc.archive.ArchiveEngineCompressor;
import com.sfc.archive.model.ArchiveProperty;
import com.sfc.archive.model.ArchiveResource;
import com.sfc.archive.model.CompressionLevel;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import net.lingala.zip4j.io.outputstream.ZipOutputStream;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.model.enums.AesKeyStrength;
import net.lingala.zip4j.model.enums.CompressionMethod;
import net.lingala.zip4j.model.enums.EncryptionMethod;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * zip4j 压缩执行器。
 */
public class Zip4jArchiveEngineCompressor implements ArchiveEngineCompressor {
    /**
     * zip4j 输出流。
     */
    private final ZipOutputStream outputStream;

    /**
     * 压缩属性。
     */
    private final ArchiveProperty property;

    /**
     * 创建 zip4j 压缩器。
     *
     * @param outputStream 输出流
     * @param property     压缩属性
     * @throws IOException 初始化输出流失败
     */
    public Zip4jArchiveEngineCompressor(OutputStream outputStream, ArchiveProperty property) throws IOException {
        this.property = property;
        char[] password = property.getEncryptionParam() == null || property.getEncryptionParam().getPassword() == null
                ? null
                : property.getEncryptionParam().getPassword().toCharArray();
        this.outputStream = password == null ? new ZipOutputStream(outputStream) : new ZipOutputStream(outputStream, password);
    }

    @Override
    public void addArchiveResource(ArchiveResource resource) throws IOException {
        if (resource == null) {
            throw new JsonException("archive resource 不能为空");
        }
        if (Boolean.FALSE.equals(resource.getIsDirectory()) && resource.getResource() == null) {
            throw new JsonException("文件资源 resource 不能为空");
        }

        String path = normalizePath(resource.getArchivePath(), Boolean.TRUE.equals(resource.getIsDirectory()));
        boolean encrypted = property.getEncryptionParam() != null && property.getEncryptionParam().getPassword() != null;

        ZipParameters parameters = new ZipParameters();
        parameters.setFileNameInZip(path);
        parameters.setCompressionMethod(Boolean.TRUE.equals(resource.getIsDirectory()) ? CompressionMethod.STORE : CompressionMethod.DEFLATE);
        parameters.setCompressionLevel(mapCompressionLevel(property.getCompressionLevel()));
        parameters.setEncryptFiles(encrypted);
        if (encrypted) {
            parameters.setEncryptionMethod(EncryptionMethod.AES);
            parameters.setAesKeyStrength(AesKeyStrength.KEY_STRENGTH_256);
        }

        if (property.getCallback() != null) {
            property.getCallback().onFileStart(path);
        }

        outputStream.putNextEntry(parameters);
        if (Boolean.FALSE.equals(resource.getIsDirectory())) {
            long loaded;
            try (InputStream in = resource.getResource().getInputStream()) {
                loaded = StreamUtils.copy(in, outputStream);
            }
            if (property.getCallback() != null) {
                long total = resource.getSize() == null ? loaded : resource.getSize();
                property.getCallback().onProgress(path, loaded, total);
            }
        }
        outputStream.closeEntry();

        if (property.getCallback() != null) {
            property.getCallback().onFileComplete(path);
        }
    }

    @Override
    public void close() throws IOException {
        outputStream.close();
    }

    /**
     * 归一化路径。
     *
     * @param archivePath 原始路径
     * @param directory   是否目录
     * @return 归一化后的路径
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

    /**
     * 映射压缩级别。
     *
     * @param level 统一压缩级别
     * @return zip4j 压缩级别
     */
    private net.lingala.zip4j.model.enums.CompressionLevel mapCompressionLevel(CompressionLevel level) {
        if (level == null) {
            return net.lingala.zip4j.model.enums.CompressionLevel.NORMAL;
        }
        return switch (level) {
            case STORE -> net.lingala.zip4j.model.enums.CompressionLevel.NO_COMPRESSION;
            case FASTEST -> net.lingala.zip4j.model.enums.CompressionLevel.FASTEST;
            case FAST -> net.lingala.zip4j.model.enums.CompressionLevel.FAST;
            case NORMAL -> net.lingala.zip4j.model.enums.CompressionLevel.NORMAL;
            case HIGH -> net.lingala.zip4j.model.enums.CompressionLevel.MAXIMUM;
            case ULTRA -> net.lingala.zip4j.model.enums.CompressionLevel.ULTRA;
        };
    }
}


