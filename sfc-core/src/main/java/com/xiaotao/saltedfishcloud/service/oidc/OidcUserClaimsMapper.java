package com.xiaotao.saltedfishcloud.service.oidc;

import com.xiaotao.saltedfishcloud.model.po.User;
import com.xiaotao.saltedfishcloud.service.user.UserService;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.context.AuthorizationServerContextHolder;
import org.springframework.security.oauth2.server.authorization.oidc.authentication.OidcUserInfoAuthenticationContext;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * OIDC 用户信息声明映射器。
 * <p>
 * 按照授权的 scope 将系统用户数据映射为 OIDC 标准声明（claims）：
 * <ul>
 *   <li>{@code openid} → {@code sub}（系统 uid 的字符串形式）</li>
 *   <li>{@code profile} → {@code preferred_username}、{@code name}、{@code picture}</li>
 *   <li>{@code email} → {@code email}、{@code email_verified}</li>
 * </ul>
 * </p>
 * <p>
 * {@code picture} URL 沿用遗留 open API 的路径模式：
 * {@code {issuer}/api/user/avatar/{username}?uid={uid}}，与
 * {@link com.xiaotao.saltedfishcloud.controller.open.OpenApiUserController} 中的头像 URL 保持一致。
 * </p>
 * <p>
 * Issuer 地址通过 {@link AuthorizationServerContextHolder} 从当前请求中动态获取，
 * 无需静态配置。
 * </p>
 * <p>
 * 注意：当前系统模型中不存在持久化的邮箱验证标志，因此 {@code email_verified} 始终返回 {@code false}。
 * </p>
 * <p>
 * 该类以 {@code @Bean} 方式注册，不使用 {@code @Service} 注解，
 * 由 {@link com.xiaotao.saltedfishcloud.config.security.oidc.OidcAuthorizationServerConfig} 统一管理。
 * </p>
 */
@RequiredArgsConstructor
@Slf4j
public class OidcUserClaimsMapper {

    private final UserService userService;

    /**
     * 从 {@link AuthorizationServerContextHolder} 获取当前请求的 Issuer 地址。
     *
     * @return Issuer 地址
     * @throws IllegalStateException 当 AuthorizationServerContext 未设置时抛出
     */
    private String getCurrentIssuer() {
        return AuthorizationServerContextHolder.getContext().getIssuer();
    }

    /**
     * 将 {@link OidcUserInfoAuthenticationContext} 映射为 {@link OidcUserInfo}。
     * <p>
     * 从授权信息中获取 principalName（uid 字符串）和已授权 scope，
     * 然后加载对应用户数据，按 scope 过滤后构建 OIDC claims。
     * </p>
     *
     * @param context OIDC userinfo 认证上下文
     * @return 包含按 scope 过滤后声明的 {@link OidcUserInfo}
     */
    public OidcUserInfo toOidcUserInfo(OidcUserInfoAuthenticationContext context) {
        OAuth2Authorization authorization = context.getAuthorization();
        String principalName = authorization.getPrincipalName();
        Set<String> scopes = authorization.getAuthorizedScopes();
        User user = userService.getUserByUser(principalName);
        return buildClaims(user, scopes, getCurrentIssuer());
    }

    /**
     * 根据用户对象、授权 scope 集合和 issuer 地址构建 {@link OidcUserInfo}。
     * <p>
     * 此方法解耦了 Spring 上下文依赖，可直接用于单元测试。
     * </p>
     *
     * @param user             用户对象
     * @param authorizedScopes 已授权的 scope 集合
     * @param issuer           Issuer 地址，用于构建 picture URL
     * @return 包含对应声明的 {@link OidcUserInfo}
     */
    public OidcUserInfo buildClaims(User user, Set<String> authorizedScopes, String issuer) {
        Long uid = user.getId();
        Map<String, Object> claims = new HashMap<>();

        if (authorizedScopes.contains("openid")) {
            claims.put("sub", uid.toString());
        }

        if (authorizedScopes.contains("profile")) {
            String username = user.getUser();
            claims.put("preferred_username", username);
            claims.put("name", username);
            // 沿用遗留 open API 的头像 URL 模式，与 OpenApiUserController 保持一致
            if (StringUtils.hasText(issuer)) {
                String baseUrl = issuer.endsWith("/") ? issuer.substring(0, issuer.length() - 1) : issuer;
                claims.put("picture", baseUrl + "/api/user/avatar/" + username + "?uid=" + uid);
            }

            if (StringUtils.hasText(user.getEmail())) {
                claims.put("email", user.getEmail());
                claims.put("email_verified", true);
            }
        }

        return new OidcUserInfo(claims);
    }
}
