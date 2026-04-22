package com.sfc.archive;

import com.sfc.archive.comporessor.ArchiveCompressor;
import com.sfc.archive.extractor.ArchiveExtractor;
import com.sfc.archive.model.ArchiveProperty;
import com.sfc.archive.model.ArchiveParam;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


@Slf4j
public class ArchiveManagerImpl implements ArchiveManager {
    private final Map<String, ArchiveCompressorProvider> compressorMap = new ConcurrentHashMap<>();
    private final Map<String, ArchiveExtractorProvider> extractorMap = new ConcurrentHashMap<>();
    private final Map<String, ArchiveEngineProvider> engineProviderMap = new ConcurrentHashMap<>();


    @Override
    public ArchiveCompressor getCompressor(ArchiveParam param, OutputStream outputStream) {
        ArchiveCompressorProvider compressorProvider = compressorMap.get(param.getType().toLowerCase());
        if (compressorProvider != null) {
            return compressorProvider.getCompressor(param, outputStream);
        } else {
            throw new JsonException("不支持压缩的类型: " + param.getType());
        }
    }

    @Override
    public ArchiveExtractor getExtractor(ArchiveParam param, Resource resource) {
        ArchiveExtractorProvider extractorProvider = extractorMap.get(param.getType().toLowerCase());
        if (extractorProvider != null) {
            return extractorProvider.getExtractor(param, resource);
        } else {
            throw new JsonException("不支持解压的类型: " + param.getType());
        }
    }

    @Override
    public void registerCompressor(ArchiveCompressorProvider provider) {
        for (String type : provider.getSupportsType()) {
            ArchiveCompressorProvider exist = compressorMap.putIfAbsent(type.toLowerCase(), provider);
            if (exist != null) {
                log.warn("压缩器类型冲突: {}，提供者将被忽略: {}", type, provider);
            }
        }
    }

    @Override
    public void registerExtractor(ArchiveExtractorProvider provider) {
        for (String type : provider.getSupportsType()) {
            ArchiveExtractorProvider exist = extractorMap.putIfAbsent(type.toLowerCase(), provider);
            if (exist != null) {
                log.warn("解压缩器类型冲突: {}，提供者将被忽略: {}", type, provider);
            }
        }
    }

    @Override
    public void registerEngineProvider(ArchiveEngineProvider provider) {
        ArchiveEngineProvider exist = engineProviderMap.putIfAbsent(provider.getId(), provider);
        if (exist != null) {
            log.warn("压缩引擎ID冲突: {}，提供者将被忽略: {}", provider.getId(), provider);
        }
    }

    @Override
    public void removeEngineProvider(String providerId) {
        engineProviderMap.remove(providerId);
    }

    @Override
    public ArchiveEngineProvider getEngineProvider(String providerId) {
        ArchiveEngineProvider provider = engineProviderMap.get(providerId);
        if (provider == null) {
            throw new JsonException("压缩引擎不存在: " + providerId);
        }
        return provider;
    }

    @Override
    public Collection<ArchiveEngineProvider> getEngineProviders() {
        return Collections.unmodifiableCollection(engineProviderMap.values());
    }

    @Override
    public ArchiveEngineProvider getDecompressorEngineByFilename(String fileName) {
        String extension = getExtension(fileName);
        if (extension == null) {
            return null;
        }
        for (ArchiveEngineProvider provider : engineProviderMap.values()) {
            for (String supportExt : provider.getSupportedDecompressExtensions()) {
                if (extension.equalsIgnoreCase(supportExt)) {
                    return provider;
                }
            }
        }
        return null;
    }

    @Override
    public ArchiveEngineCompressor createEngineCompressor(String providerId, OutputStream outputStream, ArchiveProperty property) {
        try {
            return getEngineProvider(providerId).createCompressor(outputStream, property);
        } catch (IOException e) {
            throw new JsonException("创建压缩器失败: " + e.getMessage());
        }
    }

    @Override
    public ArchiveEngineDecompressor createEngineDecompressor(String providerId, Resource resource, ArchiveProperty property) {
        try {
            return getEngineProvider(providerId).createDecompressor(resource, property);
        } catch (IOException e) {
            throw new JsonException("创建解压器失败: " + e.getMessage());
        }
    }

    /**
     * 获取文件扩展名（不含点）。
     *
     * @param fileName 文件名
     * @return 扩展名；无扩展名返回 null
     */
    private String getExtension(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return null;
        }
        int idx = fileName.lastIndexOf('.');
        if (idx < 0 || idx == fileName.length() - 1) {
            return null;
        }
        return fileName.substring(idx + 1);
    }
}
