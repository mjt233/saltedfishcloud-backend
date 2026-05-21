package com.sfc.mcp.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * MCP 文件搜索条目。
 * <p>
 * 用于表示搜索结果中的单个文件或目录及其所在路径。
 * </p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class McpFileSearchEntry {

    /**
     * 文件或目录名称。
     */
    private String name;

    /**
     * 文件大小，目录通常为 -1。
     */
    private Long size;

    /**
     * 最后修改时间戳。
     */
    private Long mtime;

    /**
     * 是否为目录。
     */
    private Boolean dir;

    /**
     * 文件 MD5，仅文件类型可能存在。
     */
    private String md5;

    /**
     * 文件或目录的完整路径（包含名称）。
     */
    private String fullPath;
}

