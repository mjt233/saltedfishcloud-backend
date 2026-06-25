package com.sfc.dm.service.identify;

import com.sfc.dm.model.dto.FileTypeCheckResult;
import com.sfc.dm.model.dto.FileTypeCheckResultDetail;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * 文件类型检测器默认实现
 */
@Getter
public class FileTypeCheckerImpl implements FileTypeChecker {
    private final List<FileTypeCheckProvider> providers = new ArrayList<>();

    /**
     * 扩展名到 Provider 的反向索引，实现 O(1) 快速查找
     */
    private Map<String, FileTypeCheckProvider> extensionProviderMap = Collections.emptyMap();

    @Override
    public void addProvider(FileTypeCheckProvider provider) {
        providers.add(provider);
    }

    /**
     * 自动注入所有{@link FileTypeCheckProvider}实现
     */
    @Autowired(required = false)
    public void setProviders(List<FileTypeCheckProvider> providers) {
        if (providers != null) {
            providers.stream()
                    .sorted(Comparator.comparingInt(FileTypeCheckProvider::getPriority))
                    .forEach(this::addProvider);
        }
        buildExtensionProviderMap();
    }

    /**
     * 构建扩展名反向索引映射表
     */
    private void buildExtensionProviderMap() {
        extensionProviderMap = providers.stream()
                .flatMap(provider -> provider.getSupportedFileExtensions().stream()
                        .map(ext -> Map.entry(ext.toLowerCase(), provider)))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (existing, incoming) -> existing,
                        LinkedHashMap::new
                ));
    }

    /**
     * 尝试使用一个 Provider 对一个文件的类型进行检测
     * @return  如果无法检测类型则返回null
     */
    private FileTypeCheckResult tryCheck(FileTypeCheckProvider provider, File file, boolean extraMetadata) {
        FileTypeCheckResultDetail detail = provider.checkFile(file, extraMetadata);
        if (detail == null) {
            return null;
        }
        FileTypeCheckResult result = new FileTypeCheckResult();
        result.setProviderId(provider.getId());
        result.setTypeId(detail.getTypeId() != null ? detail.getTypeId() : provider.getTypeId());
        result.setTypeName(detail.getTypeName() != null ? detail.getTypeName() : provider.getTypeName());
        result.setDetail(detail);
        return result;
    }

    @Override
    public FileTypeCheckResult checkFile(File file, boolean extraMetadata) {
        String fileName = file.getName().toLowerCase();

        // 文件后缀名快速匹配（O(k) map lookup, k ≤ 3）
        FileTypeCheckResult fastRes = fastCheckByExtension(fileName, file, extraMetadata);
        if (fastRes != null) {
            return fastRes;
        }

        // 逐个识别
        return providers.stream()
                .map(provider -> tryCheck(provider, file, extraMetadata))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    /**
     * 基于扩展名反向索引进行快速匹配。
     * <p>
     * 从文件名末尾向前扫描所有 '.' 位置，生成候选扩展名（如 .tar.gz → ".gz"、".tar.gz"），
     * 在 Map 中 O(1) 查找对应的 Provider。
     *
     * @param fileName      已小写的文件名
     * @param file          待检测文件
     * @param extraMetadata 是否提取元数据
     * @return 匹配结果，无匹配返回 null
     */
    private FileTypeCheckResult fastCheckByExtension(String fileName, File file, boolean extraMetadata) {
        return IntStream.iterate(fileName.length() - 1, i -> i >= 0, i -> i - 1)
                .filter(i -> fileName.charAt(i) == '.')
                .mapToObj(fileName::substring)
                .map(extensionProviderMap::get)
                .filter(Objects::nonNull)
                .findFirst()
                .map(provider -> tryCheck(provider, file, extraMetadata))
                .orElse(null);
    }
}
