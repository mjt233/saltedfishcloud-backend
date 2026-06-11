package com.sfc.dm.model.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 失效数据查询参数
 */
@Getter
@Setter
public class InvalidDataQuery {
    /**
     * 状态筛选
     */
    private String status;

    /**
     * 所属用户id筛选
     */
    private Long ownerUid;

    /**
     * 最小文件大小
     */
    private Long minFileSize;

    /**
     * 最大文件大小
     */
    private Long maxFileSize;

    /**
     * 文件类型筛选（typeId）
     */
    private String fileType;
}
