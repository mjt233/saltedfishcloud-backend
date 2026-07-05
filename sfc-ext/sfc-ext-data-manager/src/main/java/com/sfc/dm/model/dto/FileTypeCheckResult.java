package com.sfc.dm.model.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 文件类型识别结果
 */
@Getter
@Setter
public class FileTypeCheckResult {
    /**
     * 提供该结果的Provider的id
     */
    private String providerId;

    /**
     * 文件类型标识
     */
    private String typeId;

    /**
     * 文件类型名称
     */
    private String typeName;

    /**
     * 识别结果详情
     */
    private FileTypeCheckResultDetail detail;
}
