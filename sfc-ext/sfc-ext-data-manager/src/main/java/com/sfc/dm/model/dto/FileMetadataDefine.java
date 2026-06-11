package com.sfc.dm.model.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 文件元数据定义
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FileMetadataDefine {
    /**
     * 元数据的人类阅读友好名称
     */
    private String name;

    /**
     * 元数据的唯一标识符
     */
    private String key;

    /**
     * 元数据的描述信息
     */
    private String description;

    /**
     * 页面视图展示时使用的html标签或vue组件名称
     */
    private String viewTag;
}
