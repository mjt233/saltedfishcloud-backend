package com.xiaotao.saltedfishcloud.config.security;

import com.xiaotao.saltedfishcloud.model.json.JsonResultImpl;
import com.xiaotao.saltedfishcloud.service.auth.DelegationTokenAuthenticationFilter;
import com.xiaotao.saltedfishcloud.service.auth.DelegationTokenVerifier;
import com.xiaotao.saltedfishcloud.service.auth.ServiceAuthProperties;
import com.xiaotao.saltedfishcloud.service.user.UserService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.util.Arrays;

/**
 * 内部服务调用（{@code /api/internal/**}）的独立安全过滤器链。
 * <p>
 * <b>与 OIDC / OAuth 零耦合</b>：不注入任何 OIDC bean、不依赖 {@code sys.oidc.*} 配置、
 * 也不带任何 {@code @ConditionalOnProperty}。{@code sys.oidc.enabled=false} 时本链照常工作
 * ——这正是"独立性"的直接检验项。
 * </p>
 *
 * <h3>⚠️ 红线：这条链<b>必须始终注册</b></h3>
 * <p>
 * 不能因为 {@code enabled=false} 或缺密钥就不注册这条链。否则请求会落到主链的
 * {@code anyRequest().authenticated()}，变成"<b>任何登录用户都能访问内部 API</b>"
 * （用自己的 uid 读自己的文件）——一个很隐蔽的口子：
 * "降级"变成了"放开"。正确做法是<b>链始终在，条件不满足就一律 401</b>，
 * 该判定由 {@link DelegationTokenAuthenticationFilter} 完成。
 * </p>
 *
 * <h3>为什么入口点是 401 而不是 403</h3>
 * <p>
 * 主链共享的 {@code authenticationEntryPoint} 返回 403，本链<b>必须自带 401 入口点</b>：
 * 401"未认证"与 403"已认证但无权限"是两种不同的失败语义，
 * 设计文档 §3.3 要求"无令牌 / 凭证无效"统一为 401。
 * </p>
 *
 * <h3>链内过滤器为什么内联创建</h3>
 * <p>
 * {@link DelegationTokenAuthenticationFilter} 在方法内 {@code new} 出来、<b>不注册为独立 Bean</b>，
 * 避免被 Spring Boot 自动注册成全局 Servlet Filter 从而对所有路径生效——
 * 与既有的 {@code OidcResourceServerConfig} 写法一致。
 * </p>
 *
 * <h3>顺带继承的既有能力</h3>
 * <p>
 * {@code /api/internal/**} 位于 {@code /api/**} 之下，因此自动被
 * {@code ProtectBlocker} 拦截（存储模式切换保护）；而 {@code @UID}、{@code @ValidPath}、
 * {@code @RolesAllowed}、{@code @RequireScope} 也全部原样生效，
 * 因为它们读的都是 SecurityContext，而不是请求参数。
 * </p>
 */
@Configuration
@EnableConfigurationProperties(ServiceAuthProperties.class)
@RequiredArgsConstructor
public class ServiceAuthSecurityConfig {

    /** 委托令牌机制配置 */
    private final ServiceAuthProperties properties;

    /** Spring 环境，仅用于把激活的 profile 列表传给配置校验（开发模式白名单判定） */
    private final Environment environment;

    /**
     * 在上下文初始化时校验委托令牌配置。
     * <p>
     * 与 {@code OidcConfiguration.validateOidcProperty()} 的写法一致：把 profile 判定所需的
     * 激活 profile 列表作为显式参数传进去，配置类本身不实现 {@code EnvironmentAware}。
     * </p>
     *
     * @throws IllegalStateException 配置不合法时抛出，使应用快速失败
     */
    @PostConstruct
    public void validateServiceAuthProperties() {
        properties.validate(Arrays.asList(environment.getActiveProfiles()));
    }

    /**
     * 为 {@code /api/internal/**} 配置委托令牌专用安全过滤器链。
     * <p>
     * 过滤器链优先级为 {@code @Order(0)}，高于 OIDC 链（{@code @Order(1)}）与主链（{@code @Order(2)}），
     * 通过 {@code securityMatcher} 精确匹配 {@code /api/internal/**}，不影响其它端点的认证逻辑。
     * </p>
     *
     * @param http        Spring Security 配置入口
     * @param verifier    委托令牌校验器
     * @param userService 用户服务，用于按令牌 {@code sub} 装载主体
     * @return 内部 API 专用安全过滤器链
     * @throws Exception 构建安全过滤器链失败时抛出
     */
    @Bean
    @Order(0)
    public SecurityFilterChain internalApiSecurityFilterChain(
            HttpSecurity http,
            DelegationTokenVerifier verifier,
            UserService userService) throws Exception {
        DelegationTokenAuthenticationFilter delegationTokenFilter =
                new DelegationTokenAuthenticationFilter(properties, verifier, userService);

        http.securityMatcher("/api/internal/**")
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(delegationTokenFilter, UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
                .csrf(AbstractHttpConfigurer::disable)
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setContentType("application/json;charset=utf-8");
                            response.setStatus(401);
                            response.getWriter().print(JsonResultImpl.getInstance(401, null, "拒绝访问，内部服务凭证无效或缺失"));
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.setContentType("application/json;charset=utf-8");
                            response.setStatus(403);
                            response.getWriter().print(JsonResultImpl.getInstance(403, null, "拒绝访问，内部服务凭证权限不足"));
                        }));
        return http.build();
    }
}
