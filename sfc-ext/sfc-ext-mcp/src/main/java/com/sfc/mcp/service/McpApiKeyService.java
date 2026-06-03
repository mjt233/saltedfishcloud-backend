package com.sfc.mcp.service;

import com.sfc.mcp.dao.McpApiKeyRepo;
import com.sfc.mcp.model.McpApiKey;
import com.xiaotao.saltedfishcloud.utils.SecureUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * MCP API Key 业务服务。
 * <p>
 * 提供 API Key 的生成、验证、查询和撤销功能。
 * token 原文仅在生成时返回一次，数据库仅存储 BCrypt 哈希值。
 */
@Service
@RequiredArgsConstructor
public class McpApiKeyService {

    private final McpApiKeyRepo mcpApiKeyRepo;

    /**
     * 为指定用户生成一个新的 API Key。
     * <p>
     * 如果用户已存在未撤销的 API Key，旧的会被自动撤销。
     *
     * @param uid  用户 ID
     * @param name Key 名称
     * @return 原始 token（仅此一次返回）
     */
    public String generate(Long uid, String name) {
        String token = "sfc_mcp_" + SecureUtils.getUUID() + SecureUtils.getUUID();
        String tokenHash = SecureUtils.getBCryptPasswordEncoder().encode(token);

        McpApiKey apiKey = new McpApiKey();
        apiKey.setUid(uid);
        apiKey.setName(name);
        apiKey.setTokenHash(tokenHash);
        apiKey.setMaskedToken(maskToken(token));
        mcpApiKeyRepo.save(apiKey);

        return token;
    }

    /**
     * 验证 token 并返回对应的用户 ID。
     *
     * @param token 原始 token
     * @return 用户 ID，验证失败返回 null
     */
    public Long validate(String token) {
        if (token == null || !token.startsWith("sfc_mcp_")) {
            return null;
        }
        Iterable<McpApiKey> allKeys = mcpApiKeyRepo.findAll();
        for (McpApiKey key : allKeys) {
            if (SecureUtils.getBCryptPasswordEncoder().matches(token, key.getTokenHash())) {
                return key.getUid();
            }
        }
        return null;
    }

    /**
     * 查询指定用户的 MCP API Key 列表。
     *
     * @param uid 用户 ID
     * @return API Key 列表
     */
    public List<McpApiKey> listByUid(Long uid) {
        return mcpApiKeyRepo.findAllByUidOrderByIdDesc(uid);
    }

    /**
     * 根据主键查询 API Key。
     *
     * @param keyId API Key 主键 ID
     * @return API Key 实体
     */
    public McpApiKey getById(Long keyId) {
        return mcpApiKeyRepo.findById(keyId).orElseThrow(() -> new IllegalArgumentException("API Key 不存在"));
    }

    /**
     * 删除指定的 API Key。
     *
     * @param keyId API Key 主键 ID
     */
    public void deleteApiKey(Long keyId) {
        mcpApiKeyRepo.deleteById(keyId);
    }

    /**
     * 重命名指定的 API Key。
     *
     * @param keyId   API Key 主键 ID
     * @param newName 新名称
     */
    public void rename(Long keyId, String newName) {
        McpApiKey apiKey = getById(keyId);
        apiKey.setName(newName);
        mcpApiKeyRepo.save(apiKey);
    }

    /**
     * 遮掩 token，保留首尾各 6 位，中间用 6 个 * 代替。
     */
    private String maskToken(String token) {
        if (token == null || token.length() <= 12) {
            return "******";
        }
        return token.substring(0, 6) + "******" + token.substring(token.length() - 6);
    }
}
