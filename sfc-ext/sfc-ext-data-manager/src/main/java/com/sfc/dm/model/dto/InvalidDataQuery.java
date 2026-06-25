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

    /**
     * 排序字段，不传则不排序（支持 fileSize、lastModified）
     */
    private String sortBy;

    /**
     * 排序方向（ASC、DESC，默认DESC）
     */
    private String sortOrder;

    /**
     * Groovy 筛选脚本代码。
     * <p>脚本中可通过 {@code record} 变量访问每条失效数据记录（{@link com.sfc.dm.model.po.InvalidDataRecord}），
     * 最后一行表达式的布尔值决定是否保留该记录（true 保留，false 丢弃）。</p>
     * <p>仅在 POST {@code /api/dataManager/invalidData/filter} 接口中使用，GET 接口忽略此字段。</p>
     */
    private String filterScript;
}
