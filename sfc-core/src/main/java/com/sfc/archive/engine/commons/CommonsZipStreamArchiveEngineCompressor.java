package com.sfc.archive.engine.commons;

import com.sfc.archive.engine.AbstractArchiveEngineCompressor;
import com.sfc.archive.model.ArchiveEngineProperty;
import com.sfc.archive.model.ArchiveResource;
import org.apache.commons.compress.archivers.zip.UnixStat;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.attribute.FileTime;
import java.util.zip.ZipEntry;

/**
 * 基于 ZipArchiveOutputStream 的流式压缩器。
 */
public class CommonsZipStreamArchiveEngineCompressor extends AbstractArchiveEngineCompressor {

    /**
     * ZIP 输出流。
     */
    private final ZipArchiveOutputStream zipOutputStream;

    /**
     * 创建流式压缩器。
     *
     * @param targetOutput 目标输出流
     * @param property     压缩属性
     */
    public CommonsZipStreamArchiveEngineCompressor(OutputStream targetOutput, ArchiveEngineProperty property) {
        super(property);
        this.zipOutputStream = new ZipArchiveOutputStream(targetOutput);
        configureEncoding(property);
        zipOutputStream.setLevel(CommonsCompressionLevelUtils.mapDeflaterLevel(property.getCompressionLevel()));
    }

    /**
     * 配置 ZIP 条目名称编码兼容策略。
     * <p>
     * ZIP 规范中的文件名本质上是字节序列，不同解压工具会结合通用位标记与 Unicode Extra Field 进行解码。
     * 这里统一写入显式编码信息，并额外补充 Unicode 路径字段，尽量兼容 UTF-8 与本地编码两类消费端。
     * </p>
     *
     * @param property 压缩属性
     */
    private void configureEncoding(ArchiveEngineProperty property) {
        String encoding = property.getEncoding();
        boolean utf8Encoding = isUtf8Encoding(encoding);
        zipOutputStream.setEncoding(encoding);
        zipOutputStream.setUseLanguageEncodingFlag(utf8Encoding);
        zipOutputStream.setFallbackToUTF8(!utf8Encoding);
        zipOutputStream.setCreateUnicodeExtraFields(ZipArchiveOutputStream.UnicodeExtraFieldPolicy.ALWAYS);
    }

    /**
     * 判断给定编码是否为 UTF-8。
     *
     * @param encoding 编码名称
     * @return 是 UTF-8 返回 {@code true}
     */
    private boolean isUtf8Encoding(String encoding) {
        if (encoding == null || encoding.isEmpty()) {
            return true;
        }
        return StandardCharsets.UTF_8.equals(Charset.forName(encoding));
    }

    /**
     * 为当前资源创建 ZIP entry 并返回可写输出流。
     *
     * @param resource       资源信息
     * @return 文件资源返回 ZIP 输出流，目录返回 null
     * @throws IOException 创建 entry 失败
     */
    @Override
    protected OutputStream openEntryOutputStream(ArchiveResource resource) throws IOException {
        boolean directory = Boolean.TRUE.equals(resource.getIsDirectory());
        ZipArchiveEntry entry = new ZipArchiveEntry(resource.getArchivePath());

        if (directory) {
            entry.setMethod(ZipEntry.STORED);
            entry.setSize(0);
            entry.setCompressedSize(0);
            entry.setCrc(0);
            entry.setUnixMode(UnixStat.DIR_FLAG | UnixStat.DEFAULT_DIR_PERM);
        } else if (resource.getSize() != null && resource.getSize() >= 0) {
            entry.setSize(resource.getSize());
        }

        if (resource.getLastModified() != null) {
            entry.setLastModifiedTime(FileTime.fromMillis(resource.getLastModified().getTime()));
        }
        if (resource.getCreated() != null) {
            entry.setCreationTime(FileTime.fromMillis(resource.getCreated().getTime()));
        }

        zipOutputStream.putArchiveEntry(entry);
        if (directory) {
            return null;
        }
        return zipOutputStream;
    }

    /**
     * 关闭当前 ZIP entry。
     *
     * @throws IOException 关闭失败
     */
    @Override
    protected void doCloseCurrentEntry() throws IOException {
        zipOutputStream.closeArchiveEntry();
    }

    @Override
    public void close() throws IOException {
        zipOutputStream.finish();
        zipOutputStream.close();
    }
}


