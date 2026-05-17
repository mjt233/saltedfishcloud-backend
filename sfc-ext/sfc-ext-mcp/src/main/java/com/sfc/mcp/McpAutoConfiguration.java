package com.sfc.mcp;

import com.sfc.mcp.controller.McpOAuthController;
import com.sfc.mcp.model.McpProperty;
import com.sfc.mcp.prompt.McpDiskPrompt;
import com.sfc.mcp.service.McpUploadService;
import com.sfc.mcp.tools.McpDiskTools;
import com.xiaotao.saltedfishcloud.service.config.ConfigService;
import org.springframework.context.annotation.Bean;
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
		McpOAuthController.class,
		McpDiskPrompt.class,
		McpUploadService.class
})
public class McpAutoConfiguration {

	@Bean
	public McpProperty mcpProperty(ConfigService  configService) {
		McpProperty mcpProperty = new McpProperty();
		configService.bindPropertyEntity(mcpProperty);
		return mcpProperty;
	}
}

