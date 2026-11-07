package com.sfc.archive.engine.commons;

import com.sfc.archive.engine.AbstractArchiveEngineCompressor;
import com.sfc.archive.model.ArchiveEngineProperty;
import com.sfc.archive.model.ArchiveResource;
import com.sfc.archive.model.CompressionLevel;
import com.xiaotao.saltedfishcloud.utils.PathUtils;
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZMethod;
import org.apache.commons.compress.archivers.sevenz.SevenZMethodConfiguration;
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile;
import org.jetbrains.annotations.NotNull;
import org.tukaani.xz.LZMA2Options;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

/**
 * 基于 Apache Commons Compress 的 7z 压缩执行器。
 */
public class CommonsSevenZArchiveEngineCompressor extends AbstractArchiveEngineCompressor {
    /**
     * 目标输出流。
     */
    private final OutputStream targetOutputStream;

    /**
     * 临时 7z 文件路径。
     */
    private final Path tempSevenZPath;

    /**
     * 7z 输出对象。
     */
    private final SevenZOutputFile sevenZOutputFile;

    /**
     * 写入 7z 条目的输出流适配器。
     */
    private final OutputStream sevenZEntryOutputStream;

    /**
     * 创建 7z 压缩器。
     *
     * <p>压缩方法选择策略：
     * <ul>
     *   <li>{@link CompressionLevel#STORE}：使用 {@link SevenZMethod#COPY}（仅存储，不压缩）</li>
     *   <li>其他级别：使用 LZMA2，并显式构造 {@link LZMA2Options} 传递 preset（1‑9）</li>
     * </ul>
     * </p>
     *
     * @param targetOutputStream 目标输出流
     * @param property           压缩属性
     * @throws IOException 初始化失败
     */
    public CommonsSevenZArchiveEngineCompressor(OutputStream targetOutputStream, ArchiveEngineProperty property) throws IOException {
        super(property);
        this.targetOutputStream = targetOutputStream;
        this.tempSevenZPath = PathUtils.createTemplateFilePath("sfc-7z");
        boolean success = false;
        try {
            this.sevenZOutputFile = new SevenZOutputFile(tempSevenZPath.toFile());
            sevenZOutputFile.setContentMethods(Collections.singleton(buildSevenZMethodConfiguration(property.getCompressionLevel())));
            this.sevenZEntryOutputStream = new OutputStream() {
                @Override
                public void write(int b) throws IOException {
                    sevenZOutputFile.write(b);
                }

                @Override
                public void write(@NotNull byte[] b, int off, int len) throws IOException {
                    sevenZOutputFile.write(b, off, len);
                }
            };
            success = true;
        } finally {
            if (!success) {
                Files.deleteIfExists(tempSevenZPath);
            }
        }
    }

    /**
     * 根据压缩级别构建 7z 内容方法配置。
     *
     * <p>{@link CompressionLevel#STORE} 使用 {@link SevenZMethod#COPY} 以实现真正的"仅存储"，
     * 其余级别使用 LZMA2，并显式创建 {@link LZMA2Options} 以避免将 preset 整数误解释为字典大小。</p>
     *
     * @param level 通用压缩级别
     * @return 7z 方法配置
     * @throws IOException 构造 LZMA2 参数失败
     */
    private static SevenZMethodConfiguration buildSevenZMethodConfiguration(CompressionLevel level) throws IOException {
        if (level == CompressionLevel.STORE) {
            // COPY 方法：仅存储，不进行任何压缩
            return new SevenZMethodConfiguration(SevenZMethod.COPY);
        }
        return new SevenZMethodConfiguration(SevenZMethod.LZMA2, buildLzma2Options(level));
    }

    /**
     * 根据统一压缩级别构建 7z 所需的 LZMA2 参数。
     *
     * <p>注意：这里必须传入 {@link LZMA2Options} 对象，而不能直接传入 preset 数字。
     * 对于 {@link SevenZMethod#LZMA2}，数字选项会被 Apache Commons Compress 解释为字典大小（字节数），
     * 从而导致类似“6 B”这类非法字典大小异常。</p>
     *
     * @param level 通用压缩级别
     * @return LZMA2 参数对象
     * @throws IOException 设置 preset 失败
     */
    private static LZMA2Options buildLzma2Options(CompressionLevel level) throws IOException {
        LZMA2Options options = new LZMA2Options();
        options.setPreset(CommonsCompressionLevelUtils.mapXzPreset(level));
        return options;
    }

    /**
     * 为当前资源创建 7z 条目并返回可写输出流。
     *
     * @param resource 资源信息
     * @return 文件返回输出流，目录返回 null
     * @throws IOException 创建条目失败
     */
    @Override
    protected OutputStream openEntryOutputStream(ArchiveResource resource) throws IOException {
        SevenZArchiveEntry entry = new SevenZArchiveEntry();
        entry.setName(resource.getArchivePath());
        entry.setDirectory(Boolean.TRUE.equals(resource.getIsDirectory()));
        if (!entry.isDirectory() && resource.getSize() != null && resource.getSize() >= 0) {
            entry.setSize(resource.getSize());
        }
        if (resource.getLastModified() != null) {
            entry.setLastModifiedDate(resource.getLastModified());
        }
        if (resource.getCreated() != null) {
            entry.setCreationDate(resource.getCreated());
        }

        sevenZOutputFile.putArchiveEntry(entry);
        if (entry.isDirectory()) {
            return null;
        }
        return sevenZEntryOutputStream;
    }

    /**
     * 关闭当前 7z 条目。
     *
     * @throws IOException 关闭失败
     */
    @Override
    protected void doCloseCurrentEntry() throws IOException {
        sevenZOutputFile.closeArchiveEntry();
    }

    @Override
    public void close() throws IOException {
        try {
            sevenZOutputFile.finish();
            sevenZOutputFile.close();
            try (var tempInputStream = Files.newInputStream(tempSevenZPath)) {
                tempInputStream.transferTo(targetOutputStream);
            }
            targetOutputStream.flush();
        } finally {
            try {
                targetOutputStream.close();
            } finally {
                Files.deleteIfExists(tempSevenZPath);
            }
        }
    }
}

