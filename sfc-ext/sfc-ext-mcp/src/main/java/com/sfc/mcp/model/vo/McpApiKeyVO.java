package com.sfc.mcp.model.vo;

import com.sfc.mcp.model.McpApiKey;
import lombok.Data;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * MCP API Key 列表展示对象。
 * <p>
 * 用于返回当前用户可查看的 API Key 信息，不包含敏感字段。
 */
@Data
public class McpApiKeyVO {

    /**
     * API Key 主键 ID。
     */
    private Long id;

    /**
     * API Key 名称。
     */
    private String name;

    /**
     * 脱敏后的 Token 展示值。
     */
    private String maskedToken;

    /**
     * 记录创建时间。
     */
    private Date createAt;

    /**
     * 记录更新时间。
     */
    private Date updateAt;

    /**
     * 将实体对象转换为列表展示对象。
     *
     * @param apiKey API Key 实体
     * @return 列表展示对象
     */
    public static McpApiKeyVO from(McpApiKey apiKey) {
        if (apiKey == null) {
            return null;
        }
        McpApiKeyVO apiKeyVO = new McpApiKeyVO();
        apiKeyVO.setId(apiKey.getId());
        apiKeyVO.setName(apiKey.getName());
        apiKeyVO.setMaskedToken(apiKey.getMaskedToken());
        apiKeyVO.setCreateAt(apiKey.getCreateAt());
        apiKeyVO.setUpdateAt(apiKey.getUpdateAt());
        return apiKeyVO;
    }

    /**
     * 将实体列表转换为列表展示对象集合。
     *
     * @param apiKeys API Key 实体列表
     * @return 列表展示对象集合
     */
    public static List<McpApiKeyVO> from(List<McpApiKey> apiKeys) {
        if (apiKeys == null || apiKeys.isEmpty()) {
            return Collections.emptyList();
        }
        return apiKeys.stream().map(McpApiKeyVO::from).collect(Collectors.toList());
    }
}

