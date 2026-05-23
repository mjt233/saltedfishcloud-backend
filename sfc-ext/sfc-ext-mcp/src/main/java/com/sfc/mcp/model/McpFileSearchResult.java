package com.sfc.mcp.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * MCP 文件搜索结果。
 * <p>
 * 表示指定用户网盘按关键字搜索后的分页数据。
 * </p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class McpFileSearchResult {

    /**
     * 目标用户 ID。
     */
    private Long uid;

    /**
     * 搜索关键字。
     */
    private String keyword;

    /**
     * 当前页码（从 1 开始）。
     */
    private Integer page;

    /**
     * 每页大小。
     */
    private Integer size;

    /**
     * 总页数。
     */
    private Long totalPage;

    /**
     * 总记录数。
     */
    private Long totalCount;

    /**
     * 当前页的文件或目录列表。
     */
    private List<McpFileSearchEntry> entries;
}

