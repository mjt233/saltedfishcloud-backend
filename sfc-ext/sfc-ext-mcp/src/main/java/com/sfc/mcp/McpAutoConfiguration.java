package com.sfc.mcp;

import com.sfc.mcp.controller.McpApiKeyController;
import com.sfc.mcp.controller.McpDiskFileController;
import com.sfc.mcp.dao.McpApiKeyRepo;
import com.sfc.mcp.model.McpApiKey;
import com.sfc.mcp.model.McpProperty;
import com.sfc.mcp.prompt.McpDiskPrompt;
import com.sfc.mcp.security.McpApiKeyFilter;
import com.sfc.mcp.service.McpApiKeyService;
import com.sfc.mcp.service.McpFileTransferService;
import com.sfc.mcp.tools.McpDiskTools;
import com.xiaotao.saltedfishcloud.service.config.ConfigService;
import com.xiaotao.saltedfishcloud.service.user.UserService;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * MCP插件自动配置类。
 */
@Configuration
@Import({
        McpDiskTools.class,
        McpApiKeyController.class,
        McpDiskFileController.class,
        McpDiskPrompt.class,
        McpFileTransferService.class,
        McpApiKeyService.class,
})
@EnableJpaRepositories(basePackageClasses = McpApiKeyRepo.class)
@EntityScan(basePackageClasses = McpApiKey.class)
public class McpAutoConfiguration {

    @Bean
    public McpProperty mcpProperty(ConfigService configService) {
        McpProperty mcpProperty = new McpProperty();
        configService.bindPropertyEntity(mcpProperty);
        return mcpProperty;
    }

    /**
     * 创建 MCP API Key 认证过滤器。
     */
    @Bean
    public McpApiKeyFilter mcpApiKeyFilter(
            McpApiKeyService mcpApiKeyService, UserService userService) {
        return new McpApiKeyFilter(mcpApiKeyService, userService);
    }

    /**
     * 为 MCP Streamable HTTP 端点注册专用安全过滤器链。
     * <p>
     * 认证过滤器必须位于 Spring Security 过滤器链内部，
     * 否则在链外提前写入的 {@code SecurityContextHolder} 可能会被后续安全过滤器重置，
     * 从而导致已验证的 MCP token 仍被判定为未登录。
     * </p>
     *
     * @param http            Spring Security 配置入口
     * @param mcpApiKeyFilter MCP API Key 认证过滤器
     * @return MCP 专用安全过滤器链
     * @throws Exception 构建安全过滤器链失败时抛出
     */
    @Bean
    @Order(1)
    public SecurityFilterChain mcpSecurityFilterChain(
            HttpSecurity http,
            McpApiKeyFilter mcpApiKeyFilter) throws Exception {
        http.securityMatcher("/api/mcp/stream/**", "/api/mcp/diskFile/**")
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(mcpApiKeyFilter, UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.OPTIONS, "/api/mcp/stream/**", "/api/mcp/diskFile/**").permitAll()
                        .anyRequest().authenticated()
                )
                .csrf(AbstractHttpConfigurer::disable);
        return http.build();
    }
}
