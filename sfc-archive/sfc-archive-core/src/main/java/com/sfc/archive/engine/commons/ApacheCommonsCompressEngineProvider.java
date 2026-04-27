package com.sfc.archive.engine.commons;

import com.sfc.archive.ArchiveEngineCompressor;
import com.sfc.archive.ArchiveEngineDecompressor;
import com.sfc.archive.engine.AbstractArchiveEngineProvider;
import com.sfc.archive.model.ArchiveEngineProperty;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Collection;

/**
 * 基于 Apache Commons Compress 的 解压缩引擎实现。
 */
public class ApacheCommonsCompressEngineProvider extends AbstractArchiveEngineProvider {
    @Override
    public String getId() {
        return "apache-commons-compress";
    }

    @Override
    public String getName() {
        return "Apache Commons Compress";
    }

    @Override
    public Collection<String> getSupportedCompressExtensions() {
        return Arrays.asList(
                ".zip", ".7z", ".tar.gz", ".tar.xz", ".tar.bz2",
                 ".tar", ".gz", ".xz", ".bz2", ".jar"
        );
    }

    @Override
    public Collection<String> getSupportedDecompressExtensions() {
        return Arrays.asList(
                ".zip", ".7z", ".tar", ".gz", ".xz", ".bz2", ".jar",
                ".tar.gz", ".tar.xz", ".tar.bz2"
        );
    }

    @Override
    public ArchiveEngineCompressor createCompressor(OutputStream outputStream, ArchiveEngineProperty property) throws IOException {
        ArchiveEngineProperty normalized = normalizeProperty(property);
        String extension = matchCompressorExtension(normalized, null);
        ensureEncryptSupportedForCompress(extension, normalized);

        return switch (extension) {
            case ".zip", ".jar" -> new CommonsZipStreamArchiveEngineCompressor(outputStream, normalized);
            case ".tar" -> new CommonsTarArchiveEngineCompressor(outputStream, normalized);
            case ".tar.gz" -> CommonsTarArchiveEngineCompressor.gzip(outputStream, normalized);
            case ".tar.xz" -> CommonsTarArchiveEngineCompressor.xz(outputStream, normalized);
            case ".tar.bz2" -> CommonsTarArchiveEngineCompressor.bzip2(outputStream, normalized);
            case ".gz", ".xz", ".bz2" ->
                    new CommonsCompressorStreamArchiveEngineCompressor(outputStream, normalized, extension);
            case ".7z" -> new CommonsSevenZArchiveEngineCompressor(outputStream, normalized);
            default ->
                    // 不支持的格式 matchCompressorExtension 已抛出了异常，一般不会走到这里。
                    throw new UnsupportedOperationException();
        };

    }

    @Override
    public ArchiveEngineDecompressor createDecompressor(Resource resource, ArchiveEngineProperty property) throws IOException {
        ArchiveEngineProperty normalized = normalizeProperty(property);
        String extension = matchDecompressorExtension(normalized, resource.getFilename());
        ensureEncryptSupportedForDecompress(extension, normalized);
        return switch (extension) {
            case ".7z" -> new SevenZArchiveEngineDecompressor(resource, normalized);
            case ".zip", ".jar" -> new CommonsZipArchiveEngineDecompressor(resource, normalized);
            case ".tar", ".tar.gz", ".tar.xz", ".tar.bz2" ->
                    new CommonsTarArchiveEngineDecompressor(resource, normalized, extension);
            case ".gz", ".xz", ".bz2" -> new CommonsCompressorStreamArchiveEngineDecompressor(resource, extension);
            default ->
                // 不支持的格式 matchCompressorExtension 已抛出了异常，一般不会走到这里，除非在 getSupportedCompressExtensions 声明了支持格式，但未在该方法中实现。
                    throw new UnsupportedOperationException();
        };
    }

    /**
     * 校验压缩时的加密能力。
     *
     * @param extension 压缩扩展名
     * @param property  引擎参数
     */
    private void ensureEncryptSupportedForCompress(String extension, ArchiveEngineProperty property) {
        if (!hasEncryptionPassword(property)) {
            return;
        }
        // Apache Commons Compress 当前实现不支持这些格式的加密压缩。
        throw new JsonException("Apache Commons Compress 不支持该格式的加密压缩: " + extension);
    }

    /**
     * 校验解压时的解密能力。
     *
     * @param extension 解压扩展名
     * @param property  引擎参数
     */
    private void ensureEncryptSupportedForDecompress(String extension, ArchiveEngineProperty property) {
        if (!hasEncryptionPassword(property)) {
            return;
        }
        if (".7z".equals(extension)) {
            return;
        }
        // Apache Commons Compress 当前实现不支持这些格式的加密解压。
        throw new JsonException("Apache Commons Compress 不支持该格式的加密解压: " + extension);
    }

    /**
     * 判断是否配置了有效密码。
     *
     * @param property 引擎参数
     * @return 配置了密码返回 true
     */
    private boolean hasEncryptionPassword(ArchiveEngineProperty property) {
        return property.getEncryptionParam() != null
                && StringUtils.hasText(property.getEncryptionParam().getPassword());
    }
}

