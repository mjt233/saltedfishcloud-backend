package com.sfc.dm.model.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * 文件类型信息，描述一个 Provider 支持的一种文件类型
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FileTypeInfo {
    /**
     * 文件类型标识
     */
    private String typeId;

    /**
     * 文件类型名称
     */
    private String typeName;

    /**
     * 该类型支持提取的元数据定义列表
     */
    private List<FileMetadataDefine> metadataDefines;
}
