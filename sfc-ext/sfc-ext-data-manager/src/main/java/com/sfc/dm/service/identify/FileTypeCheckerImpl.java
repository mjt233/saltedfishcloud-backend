package com.sfc.dm.service.identify;

import com.sfc.dm.model.dto.FileTypeCheckResult;
import com.sfc.dm.model.dto.FileTypeCheckResultDetail;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 文件类型检测器默认实现
 */
@Getter
public class FileTypeCheckerImpl implements FileTypeChecker {
    private final List<FileTypeCheckProvider> providers = new ArrayList<>();

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

        // 文件后缀名快速匹配
        FileTypeCheckResult fastRes = providers.stream()
                .filter(provider -> provider.getSupportedFileExtensions().stream().anyMatch(fileName::endsWith))
                .findAny()
                .map(provider -> tryCheck(provider, file, extraMetadata))
                .orElse(null);
        if (fastRes != null) {
            return fastRes;
        }

        // 逐个识别
        for (FileTypeCheckProvider provider : providers) {
            FileTypeCheckResult res = tryCheck(provider, file, extraMetadata);
            if (res != null) {
                return res;
            }
        }
        return null;
    }
}
