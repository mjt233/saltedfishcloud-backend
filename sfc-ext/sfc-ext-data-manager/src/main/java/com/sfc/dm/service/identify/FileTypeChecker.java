package com.sfc.dm.service.identify;

import com.sfc.dm.model.dto.FileTypeCheckResult;

import java.io.File;
import java.util.List;

/**
 * 文件类型检测器接口
 */
public interface FileTypeChecker {
    /**
     * 注册一个FileTypeCheckProvider
     */
    void addProvider(FileTypeCheckProvider provider);

    /**
     * 获取已注册的FileTypeCheckProvider列表
     */
    List<FileTypeCheckProvider> getProviders();

    /**
     * 对文件进行类型检测与元数据提取
     * @param file 待检测的本地临时文件
     * @param extraMetadata 是否提取元数据
     * @return 识别结果，null表示无法识别
     */
    FileTypeCheckResult checkFile(File file, boolean extraMetadata);
}
