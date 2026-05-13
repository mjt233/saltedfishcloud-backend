package com.sfc.mcp;

import com.sfc.mcp.service.McpDiskToolService;
import org.springframework.ai.mcp.server.autoconfigure.McpServerAutoConfiguration;
import org.springframework.ai.mcp.server.autoconfigure.McpWebMvcServerAutoConfiguration;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * MCP插件自动配置类。
 * <p>
 * 扫描 {@code com.sfc.mcp} 包下的所有Spring组件，包括控制器和服务。
 * </p>
 */
@Configuration
@ComponentScan("com.sfc.mcp")
@ImportAutoConfiguration({
		McpServerAutoConfiguration.class,
		McpWebMvcServerAutoConfiguration.class
})
public class McpAutoConfiguration {

	/**
	 * 注册 Spring AI MCP 工具回调提供器。
	 *
	 * @param mcpDiskToolService 网盘 MCP 工具服务
	 * @return 工具回调提供器
	 */
	@Bean
	public ToolCallbackProvider mcpToolCallbackProvider(McpDiskToolService mcpDiskToolService) {
		return MethodToolCallbackProvider.builder()
				.toolObjects(mcpDiskToolService)
				.build();
	}
}

