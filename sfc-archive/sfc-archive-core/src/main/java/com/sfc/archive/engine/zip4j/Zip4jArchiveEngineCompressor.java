package com.sfc.archive.engine.zip4j;

import com.sfc.archive.engine.AbstractArchiveEngineCompressor;
import com.sfc.archive.model.ArchiveProperty;
import com.sfc.archive.model.ArchiveResource;
import com.sfc.archive.model.CompressionLevel;
import net.lingala.zip4j.io.outputstream.ZipOutputStream;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.model.enums.AesKeyStrength;
import net.lingala.zip4j.model.enums.CompressionMethod;
import net.lingala.zip4j.model.enums.EncryptionMethod;

import java.io.IOException;
import java.io.OutputStream;

/**
 * zip4j 压缩执行器。
 */
public class Zip4jArchiveEngineCompressor extends AbstractArchiveEngineCompressor {
    /**
     * zip4j 输出流。
     */
    private final ZipOutputStream zipOutputStream;

    /**
     * 创建 zip4j 压缩器。
     *
     * @param outputStream 输出流
     * @param property     压缩属性
     * @throws IOException 初始化输出流失败
     */
    public Zip4jArchiveEngineCompressor(OutputStream outputStream, ArchiveProperty property) throws IOException {
        super(property);
        char[] password = property.getEncryptionParam() == null || property.getEncryptionParam().getPassword() == null
                ? null
                : property.getEncryptionParam().getPassword().toCharArray();
        this.zipOutputStream = password == null ? new ZipOutputStream(outputStream) : new ZipOutputStream(outputStream, password);
    }

    /**
     * 为当前资源创建 zip4j entry 并返回可写输出流。
     *
     * @param resource       资源信息
     * @return 文件资源返回 zip4j 输出流，目录返回 null
     * @throws IOException 创建 entry 失败
     */
    @Override
    protected OutputStream openEntryOutputStream(ArchiveResource resource) throws IOException {
        boolean encrypted = getProperty().getEncryptionParam() != null && getProperty().getEncryptionParam().getPassword() != null;

        ZipParameters parameters = new ZipParameters();
        parameters.setFileNameInZip(resource.getArchivePath());
        parameters.setCompressionMethod(Boolean.TRUE.equals(resource.getIsDirectory()) ? CompressionMethod.STORE : CompressionMethod.DEFLATE);
        parameters.setCompressionLevel(mapCompressionLevel(getProperty().getCompressionLevel()));
        parameters.setEncryptFiles(encrypted);
        if (encrypted) {
            parameters.setEncryptionMethod(EncryptionMethod.AES);
            parameters.setAesKeyStrength(AesKeyStrength.KEY_STRENGTH_256);
        }

        zipOutputStream.putNextEntry(parameters);
        if (Boolean.TRUE.equals(resource.getIsDirectory())) {
            return null;
        }
        return zipOutputStream;
    }

    /**
     * 关闭当前 zip4j entry。
     *
     * @throws IOException 关闭失败
     */
    @Override
    protected void doCloseCurrentEntry() throws IOException {
        zipOutputStream.closeEntry();
    }

    @Override
    public void close() throws IOException {
        zipOutputStream.close();
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


