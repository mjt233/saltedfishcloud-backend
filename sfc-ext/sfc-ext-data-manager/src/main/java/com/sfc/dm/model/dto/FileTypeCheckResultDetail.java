package com.sfc.dm.model.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;

/**
 * 文件类型识别结果详情
 */
@Getter
@Setter
public class FileTypeCheckResultDetail {
    /**
     * 文件可能的拓展名
     */
    private String extension;

    /**
     * 文件的MIME类型
     */
    private String mimetype;

    /**
     * 提取的元数据
     */
    private Map<String, String> metadata;

    /**
     * 额外的提示信息
     */
    private String message;
}
