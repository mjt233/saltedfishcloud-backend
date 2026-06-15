package com.sfc.dm.model.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 失效数据查询参数
 */
@Getter
@Setter
public class InvalidDataQuery {
    /**
     * 状态筛选（多选）
     */
    private List<String> status;

    /**
     * 所属用户id筛选
     */
    private Long ownerUid;

    /**
     * 最小文件大小(Byte)
     */
    private Long minFileSize;

    /**
     * 最大文件大小(Byte)
     */
    private Long maxFileSize;

    /**
     * 文件类型筛选（typeId，多选）
     */
    private List<String> fileType;
}
