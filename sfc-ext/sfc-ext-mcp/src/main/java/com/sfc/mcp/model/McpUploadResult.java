package com.sfc.mcp.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * MCP 文件上传结果。
 * <p>
 * 用于返回文件上传后的状态与基础信息。
 * </p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class McpUploadResult {

    /**
     * 是否上传成功。
     */
    private Boolean success;

    /**
     * 上传结果说明。
     */
    private String message;

    /**
     * 上传后的文件名称。
     */
    private String filename;

    /**
     * 上传文件大小。
     */
    private Long size;

    /**
     * 文件保存路径。
     */
    private String path;
}

