package com.sfc.archive.engine.commons;

import com.sfc.archive.engine.AbstractArchiveEngineCompressor;
import com.sfc.archive.model.ArchiveEngineProperty;
import com.sfc.archive.model.ArchiveResource;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipParameters;
import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;

/**
 * 基于 TAR 归档流的压缩执行器。
 */
public class CommonsTarArchiveEngineCompressor extends AbstractArchiveEngineCompressor {
    /**
     * TAR 输出流。
     */
    private final TarArchiveOutputStream tarOutputStream;

    /**
     * 创建 tar 压缩器。
     *
     * @param targetOutput 目标输出流
     * @param property     压缩属性
     */
    public CommonsTarArchiveEngineCompressor(OutputStream targetOutput, ArchiveEngineProperty property) {
        super(property);
        this.tarOutputStream = new TarArchiveOutputStream(targetOutput, Charset.forName(property.getEncoding()).name());
        this.tarOutputStream.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
        this.tarOutputStream.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX);
    }

    /**
     * 创建 tar.gz 压缩器。
     *
     * <p>根据 {@link ArchiveEngineProperty#getCompressionLevel()} 调整 GZIP Deflate 压缩级别。</p>
     *
     * @param targetOutput 目标输出流
     * @param property     压缩属性
     * @return 压缩器实例
     * @throws IOException 初始化失败
     */
    public static CommonsTarArchiveEngineCompressor gzip(OutputStream targetOutput, ArchiveEngineProperty property) throws IOException {
        GzipParameters params = new GzipParameters();
        params.setCompressionLevel(CommonsCompressionLevelUtils.mapDeflaterLevel(property.getCompressionLevel()));
        return new CommonsTarArchiveEngineCompressor(new GzipCompressorOutputStream(targetOutput, params), property);
    }

    /**
     * 创建 tar.xz 压缩器。
     *
     * <p>根据 {@link ArchiveEngineProperty#getCompressionLevel()} 调整 XZ/LZMA2 preset（0‑9）。</p>
     *
     * @param targetOutput 目标输出流
     * @param property     压缩属性
     * @return 压缩器实例
     * @throws IOException 初始化失败
     */
    public static CommonsTarArchiveEngineCompressor xz(OutputStream targetOutput, ArchiveEngineProperty property) throws IOException {
        return new CommonsTarArchiveEngineCompressor(
                new XZCompressorOutputStream(targetOutput, CommonsCompressionLevelUtils.mapXzPreset(property.getCompressionLevel())),
                property);
    }

    /**
     * 创建 tar.bz2 压缩器。
     *
     * <p>根据 {@link ArchiveEngineProperty#getCompressionLevel()} 调整 BZip2 blockSize（1‑9）。
     * BZip2 不支持"仅存储"模式，{@link com.sfc.archive.model.CompressionLevel#STORE} 将映射为最小 block。</p>
     *
     * @param targetOutput 目标输出流
     * @param property     压缩属性
     * @return 压缩器实例
     * @throws IOException 初始化失败
     */
    public static CommonsTarArchiveEngineCompressor bzip2(OutputStream targetOutput, ArchiveEngineProperty property) throws IOException {
        return new CommonsTarArchiveEngineCompressor(
                new BZip2CompressorOutputStream(targetOutput, CommonsCompressionLevelUtils.mapBzip2BlockSize(property.getCompressionLevel())),
                property);
    }

    /**
     * 为当前资源创建 tar entry 并返回可写输出流。
     *
     * @param resource 资源信息
     * @return 文件返回 tar 输出流，目录返回 null
     * @throws IOException 创建 entry 失败
     */
    @Override
    protected OutputStream openEntryOutputStream(ArchiveResource resource) throws IOException {
        boolean directory = Boolean.TRUE.equals(resource.getIsDirectory());
        String entryName = normalizeEntryName(resource.getArchivePath(), directory);
        TarArchiveEntry entry = new TarArchiveEntry(entryName);

        if (directory) {
            entry.setSize(0L);
        } else {
            Long size = resource.getSize();
            if (size == null || size < 0) {
                throw new JsonException("tar 格式压缩文件时必须提供有效文件大小: " + resource.getArchivePath());
            }
            entry.setSize(size);
        }

        if (resource.getLastModified() != null) {
            entry.setModTime(resource.getLastModified());
        }

        tarOutputStream.putArchiveEntry(entry);
        if (directory) {
            return null;
        }
        return tarOutputStream;
    }

    /**
     * 关闭当前 tar entry。
     *
     * @throws IOException 关闭失败
     */
    @Override
    protected void doCloseCurrentEntry() throws IOException {
        tarOutputStream.closeArchiveEntry();
    }

    @Override
    public void close() throws IOException {
        tarOutputStream.finish();
        tarOutputStream.close();
    }

    /**
     * 标准化 TAR entry 路径。
     *
     * @param archivePath 原始路径
     * @param directory   是否目录
     * @return 标准路径
     */
    private String normalizeEntryName(String archivePath, boolean directory) {
        String normalized = archivePath == null ? "" : archivePath.replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (directory && !normalized.endsWith("/")) {
            normalized += "/";
        }
        return normalized;
    }
}

