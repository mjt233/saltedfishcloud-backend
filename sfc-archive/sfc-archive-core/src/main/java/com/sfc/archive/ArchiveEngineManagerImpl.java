package com.sfc.archive;

import com.sfc.archive.model.ArchiveProperty;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
public class ArchiveEngineManagerImpl implements ArchiveEngineManager {
    private final Map<String, ArchiveEngineProvider> engineProviderMap = new ConcurrentHashMap<>();

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
    public List<ArchiveEngineProvider> getDecompressProviderByFilename(String fileName) {
        String extension = getExtension(fileName);
        if (extension == null) {
            return Collections.emptyList();
        }
        return engineProviderMap.values().stream()
                .filter(provider -> provider.getSupportedDecompressExtensions().stream()
                        .anyMatch(supportExt -> isExtensionMatch(extension, supportExt)))
                .collect(Collectors.toList());
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
     * 获取文件扩展名（包含点）。
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
        return fileName.substring(idx);
    }

    /**
     * 比较两个扩展名是否等价，兼容带点与不带点写法。
     *
     * @param sourceExtension 需要匹配的扩展名
     * @param targetExtension 候选扩展名
     * @return true 表示匹配成功
     */
    private boolean isExtensionMatch(String sourceExtension, String targetExtension) {
        String normalizedSource = normalizeExtension(sourceExtension);
        String normalizedTarget = normalizeExtension(targetExtension);
        if (normalizedSource == null || normalizedTarget == null) {
            return false;
        }
        return normalizedSource.equalsIgnoreCase(normalizedTarget);
    }

    /**
     * 规范化扩展名，确保以点开头。
     *
     * @param extension 原始扩展名
     * @return 规范化后的扩展名，无法处理时返回 null
     */
    private String normalizeExtension(String extension) {
        if (extension == null) {
            return null;
        }
        String value = extension.trim();
        if (value.isEmpty()) {
            return null;
        }
        return value.charAt(0) == '.' ? value : "." + value;
    }
}
