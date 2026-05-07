package com.sfc.archive.engine.commons;

import com.sfc.archive.engine.AbstractArchiveEngineCompressor;
import com.sfc.archive.model.ArchiveEngineProperty;
import com.sfc.archive.model.ArchiveResource;
import com.sfc.archive.model.CompressionLevel;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipParameters;
import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream;

import java.io.IOException;
import java.io.OutputStream;

/**
 * 单流压缩格式（.gz/.xz/.bz2）压缩执行器。
 */
public class CommonsCompressorStreamArchiveEngineCompressor extends AbstractArchiveEngineCompressor {
    /**
     * 单流压缩输出流。
     */
    private final OutputStream compressorOutputStream;

    /**
     * 当前压缩格式扩展名。
     */
    private final String extension;

    /**
     * 是否已经写入过文件。
     */
    private boolean fileAdded;

    /**
     * 创建单流压缩执行器。
     *
     * @param targetOutput 目标输出流
     * @param property     压缩属性（含压缩级别）
     * @param extension    扩展名
     * @throws IOException 初始化失败
     */
    public CommonsCompressorStreamArchiveEngineCompressor(OutputStream targetOutput,
                                                          ArchiveEngineProperty property,
                                                          String extension) throws IOException {
        super(property);
        this.extension = extension;
        this.compressorOutputStream = createCompressorOutputStream(targetOutput, extension, property.getCompressionLevel());
    }

    /**
     * 打开当前资源输出流。
     *
     * @param resource 资源信息
     * @return 输出流
     */
    @Override
    protected OutputStream openEntryOutputStream(ArchiveResource resource) {
        if (Boolean.TRUE.equals(resource.getIsDirectory())) {
            throw new JsonException(extension + " 格式不支持目录压缩");
        }
        if (fileAdded) {
            throw new JsonException(extension + " 格式仅支持单文件压缩");
        }
        fileAdded = true;
        return compressorOutputStream;
    }

    /**
     * 单流格式无 entry 关闭逻辑。
     */
    @Override
    protected void doCloseCurrentEntry() {
    }

    @Override
    public void close() throws IOException {
        compressorOutputStream.close();
    }

    /**
     * 按扩展名及压缩级别创建压缩输出流。
     *
     * <p>
     * 注意：XZ 与 BZip2 不支持"仅存储"模式，
     * {@link CompressionLevel#STORE} 将映射为其最低压缩开销的等效选项。
     * </p>
     *
     * @param targetOutput 目标输出流
     * @param extension    扩展名
     * @param level        压缩级别
     * @return 压缩输出流
     * @throws IOException 初始化失败
     */
    private OutputStream createCompressorOutputStream(OutputStream targetOutput, String extension, CompressionLevel level) throws IOException {
        if (".gz".equals(extension)) {
            GzipParameters params = new GzipParameters();
            params.setCompressionLevel(CommonsCompressionLevelUtils.mapDeflaterLevel(level));
            return new GzipCompressorOutputStream(targetOutput, params);
        }
        if (".xz".equals(extension)) {
            return new XZCompressorOutputStream(targetOutput, CommonsCompressionLevelUtils.mapXzPreset(level));
        }
        if (".bz2".equals(extension)) {
            return new BZip2CompressorOutputStream(targetOutput, CommonsCompressionLevelUtils.mapBzip2BlockSize(level));
        }
        throw new JsonException("不支持的单流压缩格式: " + extension);
    }
}

