package com.sfc.archive.engine.commons;

import com.sfc.archive.model.CompressionLevel;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream;

import java.util.zip.Deflater;

/**
 * Apache Commons Compress 压缩级别映射工具类。
 *
 * <p>将统一的 {@link CompressionLevel} 枚举映射到各底层格式所需的具体参数。</p>
 */
final class CommonsCompressionLevelUtils {

    private CommonsCompressionLevelUtils() {
    }

    /**
     * 将通用压缩级别映射为 Deflate 压缩级别（适用于 ZIP / GZIP）。
     *
     * <p>取值范围：{@code 0}（{@link Deflater#NO_COMPRESSION}，无压缩但仍包装格式头）到
     * {@code 9}（{@link Deflater#BEST_COMPRESSION}，最高压缩率），{@code -1} 表示默认级别（等效约为 6）。</p>
     *
     * @param level 通用压缩级别，传 {@code null} 时使用默认值
     * @return Deflate 级别
     */
    static int mapDeflaterLevel(CompressionLevel level) {
        if (level == null) {
            return Deflater.DEFAULT_COMPRESSION;
        }
        return switch (level) {
            case STORE -> Deflater.NO_COMPRESSION;
            case FASTEST -> Deflater.BEST_SPEED;
            case FAST -> 3;
            case NORMAL -> Deflater.DEFAULT_COMPRESSION;
            case HIGH -> 7;
            case ULTRA -> Deflater.BEST_COMPRESSION;
        };
    }

    /**
     * 将通用压缩级别映射为 XZ preset（适用于 {@code .xz} 与 {@code .tar.xz} 输出流构造）。
     *
     * <p>取值范围：{@code 0}（最快速、最低压缩率）到 {@code 9}（最高压缩率），默认值为 {@code 6}。
     * XZ 格式不支持真正的"仅存储"模式，{@link CompressionLevel#STORE} 将映射为 preset {@code 0}（最低压缩开销）。</p>
     *
     * <p>注意：该返回值仅适用于需要 preset 整数的 XZ API，不能直接作为 7z
     * {@code SevenZMethodConfiguration(LZMA2, options)} 的 options 参数传入，
     * 否则会被解释为字典大小而不是 preset。</p>
     *
     * @param level 通用压缩级别，传 {@code null} 时使用默认值 6
     * @return XZ preset
     */
    static int mapXzPreset(CompressionLevel level) {
        if (level == null) {
            return 6;
        }
        return switch (level) {
            case STORE -> 0;
            case FASTEST -> 1;
            case FAST -> 3;
            case NORMAL -> 6;
            case HIGH -> 7;
            case ULTRA -> 9;
        };
    }

    /**
     * 将通用压缩级别映射为 BZip2 blockSize（适用于 .bz2 及 .tar.bz2）。
     *
     * <p>取值范围：{@code 1}（{@link BZip2CompressorOutputStream#MIN_BLOCKSIZE}，最快速）到
     * {@code 9}（{@link BZip2CompressorOutputStream#MAX_BLOCKSIZE}，最高压缩率）。
     * BZip2 不支持真正的"仅存储"模式，{@link CompressionLevel#STORE} 将映射为最小 block 大小。</p>
     *
     * @param level 通用压缩级别，传 {@code null} 时使用最高压缩率
     * @return BZip2 blockSize
     */
    static int mapBzip2BlockSize(CompressionLevel level) {
        if (level == null) {
            return BZip2CompressorOutputStream.MAX_BLOCKSIZE;
        }
        return switch (level) {
            case STORE -> BZip2CompressorOutputStream.MIN_BLOCKSIZE;
            case FASTEST -> 1;
            case FAST -> 3;
            case NORMAL -> 5;
            case HIGH -> 7;
            case ULTRA -> BZip2CompressorOutputStream.MAX_BLOCKSIZE;
        };
    }
}

