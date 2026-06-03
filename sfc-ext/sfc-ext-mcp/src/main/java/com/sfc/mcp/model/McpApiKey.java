package com.sfc.mcp.model;

import com.xiaotao.saltedfishcloud.model.template.AuditModel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * MCP 用户级 API Key 实体。
 * <p>
 * 每个用户可拥有一个 MCP API Key，用于 MCP 客户端认证。
 * token 字段存储 BCrypt 哈希值，原始 token 仅在生成时返回一次。
 */
@Entity
@Data
@EqualsAndHashCode(callSuper = true)
@Table(indexes = {
        @Index(name = "idx_mcp_api_key_uid", columnList = "uid", unique = true)
})
public class McpApiKey extends AuditModel {

    /**
     * API Key 名称，便于用户识别
     */
    @Column(length = 64)
    private String name;

    /**
     * API Key 的 BCrypt 哈希值
     */
    @Column(length = 128, nullable = false)
    private String tokenHash;

    /**
     * 脱敏后的 token 展示值（前6位 + ****** + 后6位），用于前端展示
     */
    @Column(length = 32)
    private String maskedToken;
}
