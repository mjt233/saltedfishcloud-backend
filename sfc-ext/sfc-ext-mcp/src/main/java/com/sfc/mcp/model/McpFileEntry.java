package com.sfc.mcp.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * MCP 文件条目。
 * <p>
 * 用于表示网盘目录中的一个文件或文件夹。
 * </p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class McpFileEntry {

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
}

