package com.sfc.dm.model.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 批量认领预览结果项。
 * <p>展示一条失效数据认领后将保存到的路径与文件名。</p>
 */
@Getter
@Setter
public class ClaimPreviewItem {
    /**
     * 失效数据记录ID
     */
    private Long invalidDataId;

    /**
     * 原始物理存储文件名（取自 storagePath 最后一段）
     */
    private String originalFileName;

    /**
     * 解析后的保存目录路径
     */
    private String resolvedPath;

    /**
     * 解析后的保存文件名
     */
    private String resolvedFileName;

    /**
     * 文件类型标识（typeId，识别后填充）
     */
    private String fileType;

    /**
     * 文件大小（Byte）
     */
    private Long fileSize;

    /**
     * 识别出的文件扩展名（可能为 null）
     */
    private String extension;
}
