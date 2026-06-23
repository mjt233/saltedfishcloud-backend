package com.xiaotao.saltedfishcloud.config.security.oidc;

import com.xiaotao.saltedfishcloud.constant.SysRole;
import com.xiaotao.saltedfishcloud.model.po.User;
import com.xiaotao.saltedfishcloud.model.po.UserPrincipal;
import com.xiaotao.saltedfishcloud.service.user.UserService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * OIDC Access Token 认证过滤器。
 * <p>
 * 拦截 {@code Authorization: Bearer} 格式的请求，通过 {@link OAuth2AuthorizationService}
 * 在数据库中查找并校验 OIDC access token，将认证信息写入 Spring Security 上下文。
 * </p>
 * <p>
 * 认证成功后授予 {@code ROLE_OAUTH_USER} 角色以及与已授权 scope 对应的 {@code SCOPE_*} 权限，
 * 使 {@code /api/openApi/**} 端点的 {@code @RolesAllowed(OAUTH_USER)} 和
 * {@code @PreAuthorize("hasAuthority('SCOPE_*')")} 校验通过。
 * </p>
 * <p>
 * 该过滤器将 JWT access token 当作 opaque token 处理（通过 DB 查找校验），
 * 天然支持 token 撤销检查。principal 类型为 {@link UserPrincipal}，
 * 兼容 {@code @AuthenticationPrincipal} 和 {@code SecureUtils.getSpringSecurityUser()}。
 * </p>
 * <p>
 * 设计参考 {@code McpApiKeyFilter}，二者模式一致：DB 查找 token → 加载用户 →
 * 授予 {@code ROLE_OAUTH_USER} + {@code SCOPE_*} → 设入 SecurityContext。
 * </p>
 */
@Slf4j
public class OidcAccessTokenFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final OAuth2AuthorizationService authorizationService;
    private final UserService userService;

    /**
     * 构造 OIDC access token 认证过滤器。
     *
     * @param authorizationService OAuth2 授权服务，用于 token 查找与撤销校验
     * @param userService          用户服务，用于根据 principalName 加载用户
     */
    public OidcAccessTokenFilter(OAuth2AuthorizationService authorizationService, UserService userService) {
        this.authorizationService = authorizationService;
        this.userService = userService;
    }

    /**
     * 从请求中提取并校验 OIDC access token，认证成功后设入 Spring Security 上下文。
     *
     * @param request     HTTP 请求
     * @param response    HTTP 响应
     * @param filterChain 过滤器链
     * @throws ServletException Servlet 异常
     * @throws IOException      IO 异常
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(BEARER_PREFIX.length());
        if (!StringUtils.hasText(token)) {
            filterChain.doFilter(request, response);
            return;
        }

        OAuth2Authorization oAuth2Authorization = authorizationService.findByToken(token, OAuth2TokenType.ACCESS_TOKEN);
        if (oAuth2Authorization == null) {
            filterChain.doFilter(request, response);
            return;
        }

        OAuth2Authorization.Token<OAuth2AccessToken> accessToken = oAuth2Authorization.getAccessToken();
        if (accessToken == null || accessToken.isInvalidated()) {
            filterChain.doFilter(request, response);
            return;
        }

        OAuth2AccessToken oAuth2AccessToken = accessToken.getToken();
        if (oAuth2AccessToken.getExpiresAt() != null && oAuth2AccessToken.getExpiresAt().isBefore(Instant.now())) {
            filterChain.doFilter(request, response);
            return;
        }

        String principalName = oAuth2Authorization.getPrincipalName();
        User user = userService.getUserByUser(principalName);
        if (user == null) {
            filterChain.doFilter(request, response);
            return;
        }

        UserPrincipal principal = UserPrincipal.from(user);
        Set<String> authorizedScopes = oAuth2Authorization.getAuthorizedScopes();

        List<GrantedAuthority> authorities = new ArrayList<>(principal.getAuthorities());
        authorities.add(new SimpleGrantedAuthority("ROLE_" + SysRole.OAUTH_USER));
        for (String scope : authorizedScopes) {
            authorities.add(new SimpleGrantedAuthority("SCOPE_" + scope));
        }

        UsernamePasswordAuthenticationToken authenticationToken =
                new UsernamePasswordAuthenticationToken(principal, token, authorities);
        SecurityContextHolder.getContext().setAuthentication(authenticationToken);

        filterChain.doFilter(request, response);
    }
}
