package com.sfc.dm.service.identify.metadata;

import com.sfc.dm.model.dto.FileMetadataDefine;
import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * 文件元数据提取器接口
 */
public interface FileMetadataExtractor {
    /**
     * 获取声明的能提取何种文件类型标识的元数据
     * @return 类型标识，如 "document"、"image"
     */
    String getTypeId();

    /**
     * 获取文件类型名称
     * @return 人类可读的类型名称
     */
    String getTypeName();

    /**
     * 获取该提取器支持的元数据定义列表
     * @return 元数据定义列表
     */
    List<FileMetadataDefine> getMetadataDefines();

    /**
     * 提取文件的元数据
     * @param file     待提取的文件
     * @return 元数据键值对，无法提取时返回 null
     */
    Map<String, String> extract(File file);
}
