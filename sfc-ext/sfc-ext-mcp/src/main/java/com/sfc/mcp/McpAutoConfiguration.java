package com.sfc.mcp;

import com.sfc.mcp.controller.McpOAuthController;
import com.sfc.mcp.service.McpDiskTools;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * MCP插件自动配置类。
 * <p>
 * 扫描 {@code com.sfc.mcp} 包下的所有Spring组件，包括控制器和服务。
 * </p>
 */
@Configuration
@Import({
		McpOAuthAppInitializer.class,
		McpDiskTools.class,
		McpOAuthController.class
})
//@ComponentScan("com.sfc.mcp")
public class McpAutoConfiguration {

//	/**
//	 * 注册 Spring AI MCP 工具回调提供器。
//	 *
//	 * @param mcpDiskTools 网盘 MCP 工具服务
//	 * @return 工具回调提供器
//	 */
//	@Bean
//	public ToolCallbackProvider mcpToolCallbackProvider(McpDiskTools mcpDiskTools) {
//		return MethodToolCallbackProvider.builder()
//				.toolObjects(mcpDiskTools)
//				.build();
//	}
}

