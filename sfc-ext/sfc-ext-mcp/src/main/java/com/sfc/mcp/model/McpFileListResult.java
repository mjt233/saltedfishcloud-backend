package com.sfc.mcp.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * MCP 文件列表结果。
 * <p>
 * 表示某个目录下的目录项与文件项列表。
 * </p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class McpFileListResult {

    /**
     * 目标用户 ID。
     */
    private Long uid;

    /**
     * 被查询的目录路径。
     */
    private String path;

    /**
     * 当前目录下的子目录列表。
     */
    private List<McpFileEntry> dirs;

    /**
     * 当前目录下的文件列表。
     */
    private List<McpFileEntry> files;
}

