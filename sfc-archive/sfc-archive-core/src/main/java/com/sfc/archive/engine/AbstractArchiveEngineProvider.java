package com.sfc.archive.engine;

import com.sfc.archive.ArchiveEngineProvider;
import com.sfc.archive.model.ArchiveEngineProperty;
import com.sfc.archive.model.CompressionLevel;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Optional;

/**
 * 压缩引擎提供者抽象基类，封装公共默认行为。
 */
public abstract class AbstractArchiveEngineProvider implements ArchiveEngineProvider {
    /**
     * 规范化属性，补齐默认值。
     *
     * @param property 原始属性
     * @return 可用属性
     */
    protected ArchiveEngineProperty normalizeProperty(ArchiveEngineProperty property) {
        if (property == null) {
            return ArchiveEngineProperty.builder().build();
        }
        if (property.getCompressionLevel() == null) {
            property.setCompressionLevel(CompressionLevel.NORMAL);
        }
        if (property.getEncoding() == null || property.getEncoding().isEmpty()) {
            property.setEncoding("UTF-8");
        }
        return property;
    }

    /**
     * 匹配一个解压器格式，如果没有匹配的则会直接抛出异常
     * @param property  压缩引擎参数，可能包含用户指定的扩展名，优先以该值精准匹配为准
     * @param filename  待解压的压缩包的输出文件名
     */
    protected @NotNull String matchDecompressorExtension(ArchiveEngineProperty property, String filename) {
        String extension = matchExtension(property, filename, getSupportedDecompressExtensions());
        if (extension == null) {
            throw new JsonException(this.getName() + " 不支持该解压格式");
        }
        return extension;
    }

    /**
     * 匹配一个压缩器格式，如果没有匹配的则会直接抛出异常
     * @param property  压缩引擎参数，可能包含用户指定的扩展名，优先以该值精准匹配为准
     * @param filename  创建的压缩包的输出文件名
     */
    protected @NotNull String matchCompressorExtension(ArchiveEngineProperty property, String filename) {
        String extension = matchExtension(property, filename, getSupportedCompressExtensions());
        if (extension == null) {
            throw new JsonException(this.getName() + " 不支持该压缩格式");
        }
        return extension;
    }

    /**
     * 为待压缩 / 待解压的数据匹配一个支持的压缩 / 解压缩格式
     * @param property  解压属性，可能包含用户指定的扩展名
     * @param filename  待解压 或 新创建的压缩文件 的文件名
     * @param candidateExtensions 该引擎支持的压缩 / 解压缩格式列表
     * @return  返回从 <code>candidateExtensions</code> 中匹配的格式，如果没有匹配的则返回null
     */
    protected @Nullable String matchExtension(ArchiveEngineProperty property, @Nullable String filename, Collection<String> candidateExtensions) {
        // 检查是否直接指定了格式，如果有直接指定则以指定的为准
        String specifyExtension = Optional.ofNullable(property.getExtension())
                .filter(StringUtils::hasText)
                .map(String::toLowerCase)
                .orElse(null);
        if (specifyExtension != null) {
            if (candidateExtensions.contains(specifyExtension)) {
                return specifyExtension;
            } else {
                return null;
            }
        }

        // 没有指定有效的格式，则根据文件名匹配末尾
        String availableFilename = Optional.ofNullable(filename)
                .filter(StringUtils::hasText)
                .map(String::toLowerCase)
                .orElse(null);
        if (availableFilename != null) {
            // 优先使用文件名长的格式匹配，防止 .tar.gz 匹配到 .gz 的格式（.tar.gz应作为一个独立的格式）
            return candidateExtensions.stream()
                    .sorted(Comparator.comparingInt(String::length).reversed())
                    .filter(availableFilename::endsWith)
                    .findFirst()
                    .orElse(null);
        }

        return null;
    }

    @Override
    public boolean supportEncrypt() {
        return false;
    }

    @Override
    public boolean supportDecrypt() {
        return false;
    }

    @Override
    public Collection<String> getSupportedCompressExtensions() {
        return Collections.emptyList();
    }

    @Override
    public Collection<String> getSupportedDecompressExtensions() {
        return Collections.emptyList();
    }
}

