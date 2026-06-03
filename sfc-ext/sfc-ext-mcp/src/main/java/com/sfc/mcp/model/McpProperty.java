package com.sfc.mcp.model;

import com.xiaotao.saltedfishcloud.annotations.ConfigProperty;
import com.xiaotao.saltedfishcloud.annotations.ConfigPropertyEntity;
import lombok.Data;

@ConfigPropertyEntity(prefix = "mcp")
@Data
public class McpProperty {

    @ConfigProperty(
            title = "MCP 完整 HTTP Streamable 端点地址",
            describe = "用于在MCP接入说明中展示给用户",
            defaultValue = "/api/mcp/stream"
    )
    private String mcpStreamableEndPoint;

    @ConfigProperty(
            title = "文件上传的OAuth接口URL",
            describe = "MCP 服务会通过 Prompt 引导 Agent 使用该接口上传文件",
            defaultValue = "/api/openApi/diskFile/upload/v1"
    )
    private String oauthUploadApiUrl;

    @ConfigProperty(
            title = "文件下载的OAuth接口URL",
            describe = "MCP 服务会通过 Prompt 引导 Agent 使用该接口下载文件",
            defaultValue = "/api/openApi/diskFile/download/v1"
    )
    private String oauthDownloadApiUrl;
}
