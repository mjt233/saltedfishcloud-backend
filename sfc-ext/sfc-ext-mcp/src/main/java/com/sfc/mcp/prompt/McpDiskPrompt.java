package com.sfc.mcp.prompt;

import com.sfc.mcp.service.McpFileTransferService;
import com.xiaotao.saltedfishcloud.utils.RequestContextUtils;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.GetPromptResult;
import io.modelcontextprotocol.spec.McpSchema.PromptMessage;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springaicommunity.mcp.annotation.McpPrompt;

import java.util.List;

@RequiredArgsConstructor
public class McpDiskPrompt {
    private final McpFileTransferService mcpFileTransferService;

    @McpPrompt(name = "upload_file", title = "上传文件到咸鱼云网盘", description = "将文件上传到咸鱼云网盘")
    public GetPromptResult uploadFile() {
        HttpServletRequest request = RequestContextUtils.getHttpServletRequest().orElseThrow();

        return new GetPromptResult(
                "上传文件到咸鱼云网盘",
                List.of(new PromptMessage(McpSchema.Role.ASSISTANT, new TextContent(mcpFileTransferService.getUploadPrompt(request))))
        );
    }
}
