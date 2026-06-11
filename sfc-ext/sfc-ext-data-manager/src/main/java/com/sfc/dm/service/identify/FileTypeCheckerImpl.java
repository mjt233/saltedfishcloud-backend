package com.sfc.dm.service.identify;

import com.sfc.dm.model.dto.FileTypeCheckResult;
import com.sfc.dm.model.dto.FileTypeCheckResultDetail;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.File;
import java.util.ArrayList;
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
            providers.forEach(this::addProvider);
        }
    }

    @Override
    public FileTypeCheckResult checkFile(File file, boolean extraMetadata) {
        String fileName = file.getName().toLowerCase();
        for (FileTypeCheckProvider provider : providers) {
            // 快速文件名匹配
            boolean matched = provider.getSupportedFileExtensions().stream()
                    .anyMatch(ext -> fileName.endsWith(ext.toLowerCase()));
            if (!matched) {
                continue;
            }
            // 实际识别
            FileTypeCheckResultDetail detail = provider.checkFile(file, extraMetadata);
            if (detail != null) {
                FileTypeCheckResult result = new FileTypeCheckResult();
                result.setProviderId(provider.getId());
                result.setTypeId(provider.getTypeId());
                result.setTypeName(provider.getTypeName());
                result.setDetail(detail);
                return result;
            }
        }
        return null;
    }
}
