package com.sfc.dm.model.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * 文件类型识别提供者信息
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FileTypeProviderInfo {
    /**
     * 提供者唯一标识
     */
    private String id;

    /**
     * 文件类型标识
     */
    private String typeId;

    /**
     * 文件类型名称
     */
    private String typeName;

    /**
     * 支持的文件扩展名列表
     */
    private List<String> supportedExtensions;

    /**
     * 支持提取的元数据定义列表
     */
    private List<FileMetadataDefine> metadataDefines;
}
