package com.sfc.mcp.constant;

/**
 * MCP 模块常量定义。
 */
public final class McpConstant {

    /**
     * MCP OAuth 应用名称，用于查找应用。
     */
    public static final String MCP_OAUTH_APP_NAME = "咸鱼云网盘MCP服务";

    /**
     * MCP OAuth clientSecret 缓存 key 前缀。
     */
    public static final String MCP_OAUTH_CLIENT_SECRET_CACHE_PREFIX = "sfc:mcp:oauth:client_secret:";

    /**
     * MCP OAuth 初始化分布式锁缓存 key 前缀。
     */
    public static final String MCP_OAUTH_INIT_LOCK_CACHE_PREFIX = "sfc:mcp:oauth:init_lock:";

    private McpConstant() {}
}
