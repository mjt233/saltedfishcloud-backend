package com.sfc.dm.service.identify.metadata;

import java.io.File;
import java.util.Map;

/**
 * 文件元数据提取器接口
 */
public interface FileMetadataExtractor {
    /**
     * 提取文件的元数据
     * @param file     待提取的文件
     * @param mimeType Tika 检测到的 MIME 类型
     * @return 元数据键值对，无法提取时返回 null
     */
    Map<String, String> extract(File file, String mimeType);
}
