package com.xiaotao.saltedfishcloud.config.security.oidc;

import com.xiaotao.saltedfishcloud.model.json.JsonResultImpl;
import com.xiaotao.saltedfishcloud.service.user.UserService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * OIDC 资源服务器安全配置。
 * <p>
 * 为 {@code /api/openApi/**} 端点配置专用的安全过滤器链，
 * 使用 {@link OidcAccessTokenFilter} 校验 OIDC access token，
 * 授予 {@code ROLE_OAUTH_USER} 和 {@code SCOPE_*} 权限，
 * 使 OpenAPI 控制器的 {@code @RolesAllowed(OAUTH_USER)} 和
 * {@code @PreAuthorize("hasAuthority('SCOPE_*')")} 校验通过。
 * </p>
 * <p>
 * 仅在 {@code sys.oidc.enabled=true} 时生效。过滤器链优先级为 {@code @Order(1)}，
 * 高于主链（{@code @Order(2)}），通过 {@code securityMatcher} 精确匹配
 * {@code /api/openApi/**} 路径，不影响其他端点的认证逻辑。
 * </p>
 * <p>
 * 过滤器在安全链内内联创建（不注册为独立 Bean），
 * 避免被 Spring Boot 自动注册为全局 Servlet Filter，
 * 从而仅对 {@code /api/openApi/**} 请求生效。
 * </p>
 */
@Configuration
@ConditionalOnProperty(name = "sys.oidc.enabled", havingValue = "true")
public class OidcResourceServerConfig {

    /**
     * 为 {@code /api/openApi/**} 端点配置专用安全过滤器链。
     * <p>
     * 使用 {@link OidcAccessTokenFilter} 校验 {@code Authorization: Bearer} 格式的
     * OIDC access token，认证成功后授予 {@code ROLE_OAUTH_USER} 和 {@code SCOPE_*} 权限。
     * 未认证或 token 无效时返回 403 JSON 响应，与主链异常处理保持一致。
     * </p>
     *
     * @param http                 Spring Security 配置入口
     * @param authorizationService OAuth2 授权服务，用于 token 查找与撤销校验
     * @param userService          用户服务，用于根据 principalName 加载用户
     * @return OpenAPI 专用安全过滤器链
     * @throws Exception 构建安全过滤器链失败时抛出
     */
    @Bean
    @Order(1)
    public SecurityFilterChain openApiSecurityFilterChain(
            HttpSecurity http,
            OAuth2AuthorizationService authorizationService,
            UserService userService) throws Exception {
        OidcAccessTokenFilter oidcAccessTokenFilter = new OidcAccessTokenFilter(authorizationService, userService);
        http.securityMatcher("/api/openApi/**")
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(oidcAccessTokenFilter, UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(authorize -> authorize
                        .anyRequest().authenticated()
                )
                .csrf(AbstractHttpConfigurer::disable)
                .exceptionHandling(ex -> ex.authenticationEntryPoint((request, response, authException) -> {
                    response.setContentType("application/json;charset=utf-8");
                    response.setStatus(403);
                    response.getWriter().print(JsonResultImpl.getInstance(403, null, "拒绝访问，权限不足或登录已过期/无效"));
                }));
        return http.build();
    }
}
