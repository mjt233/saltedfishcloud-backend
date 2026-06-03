package com.sfc.mcp.dao;

import com.sfc.mcp.model.McpApiKey;
import com.xiaotao.saltedfishcloud.dao.BaseRepo;

import java.util.List;

/**
 * MCP API Key 持久化仓库。
 */
public interface McpApiKeyRepo extends BaseRepo<McpApiKey> {

	/**
	 * 查询指定用户的 MCP API Key 列表。
	 *
	 * @param uid 用户 ID
	 * @return API Key 列表
	 */
	List<McpApiKey> findAllByUidOrderByIdDesc(Long uid);
}
