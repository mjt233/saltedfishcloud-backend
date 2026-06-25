package com.sfc.dm.model.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 脚本筛选结果
 */
@Getter
@Setter
public class InvalidDataFilterResult {
    /**
     * 筛选结果缓存ID，用于后续分页查询
     */
    private String filterId;

    /**
     * 筛选后匹配的记录总数
     */
    private long matchedCount;
}
