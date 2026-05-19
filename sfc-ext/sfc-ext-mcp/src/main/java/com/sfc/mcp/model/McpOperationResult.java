package com.sfc.mcp.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * MCP 通用操作结果。
 * <p>
 * 用于返回删除、复制、移动、重命名、创建目录等操作的执行结果。
 * </p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class McpOperationResult {

    /**
     * 是否执行成功。
     */
    private Boolean success;

    /**
     * 操作结果说明。
     */
    private String message;

    /**
     * 本次操作影响的数量。
     */
    private Long affectedCount;
}

