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

}
