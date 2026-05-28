package com.xiaotao.saltedfishcloud.service.oidc;

import com.xiaotao.saltedfishcloud.model.po.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames;
import org.springframework.security.oauth2.server.authorization.token.JwtGenerator;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;

import java.time.Instant;
import java.util.Set;

/**
 * 自定义 OIDC Token 生成器。
 * <p>
 * 将 Spring Authorization Server 的 token 生成请求桥接到遗留 token 体系：
 * <ul>
 *   <li>{@code access_token} → 委托给 {@link OidcTokenBridgeService#issueApiTicket(Long, Long, String)} 生成 15 分钟短期 ApiTicket</li>
 *   <li>{@code refresh_token} → 委托给 {@link OidcTokenBridgeService#issueLegacyAccessToken(Long, Long)} 生成长期遗留 Access Token</li>
 *   <li>{@code id_token} → 委托给 Spring 的 {@link JwtGenerator} 生成标准 OIDC ID Token</li>
 * </ul>
 * </p>
 * <p>注意：uid 从认证主体中提取——初次授权码兑换时 principal 为 {@link UserPrincipal}，
 * 通过 {@link UserPrincipal#getId()} 获取；refresh_token 刷新时 principal name 即为 uid 字符串。</p>
 */
@RequiredArgsConstructor
public class OidcTokenGenerator implements OAuth2TokenGenerator<OAuth2Token> {

    /** access_token 有效期（秒） */
    private static final long ACCESS_TOKEN_TTL_SECONDS = 15 * 60;

    private final OidcTokenBridgeService bridgeService;
    private final JwtGenerator idTokenGenerator;

    /**
     * {@inheritDoc}
     *
     * @param context token 生成上下文
     * @return 生成的 token，类型未知时返回 {@code null}
     */
    @Override
    public OAuth2Token generate(OAuth2TokenContext context) {
        OAuth2TokenType tokenType = context.getTokenType();

        if (OAuth2TokenType.ACCESS_TOKEN.equals(tokenType)) {
            return generateAccessToken(context);
        } else if (OAuth2TokenType.REFRESH_TOKEN.equals(tokenType)) {
            return generateRefreshToken(context);
        } else if (new OAuth2TokenType(OidcParameterNames.ID_TOKEN).equals(tokenType)) {
            return idTokenGenerator.generate(context);
        }
        return null;
    }

    /**
     * 生成短期 ApiTicket 作为 OIDC {@code access_token}。
     *
     * @param context token 上下文
     * @return 有效期 15 分钟的 {@link OAuth2AccessToken}
     */
    private OAuth2AccessToken generateAccessToken(OAuth2TokenContext context) {
        Long appId = extractAppId(context);
        Long uid = extractUid(context);
        String scope = buildScopeString(context.getAuthorizedScopes());
        if (!scope.contains(OidcScopes.OPENID)) {
            scope = scope + " " + OidcScopes.OPENID;
        }

        String ticketValue = bridgeService.issueApiTicket(appId, uid, scope);

        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plusSeconds(ACCESS_TOKEN_TTL_SECONDS);

        return new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                ticketValue,
                issuedAt,
                expiresAt
        );
    }

    /**
     * 生成长期遗留 Access Token 作为 OIDC {@code refresh_token}。
     * 返回的 {@link OAuth2RefreshToken} 不设置过期时间（{@code expiresAt=null}）。
     *
     * @param context token 上下文
     * @return 无过期时间的 {@link OAuth2RefreshToken}
     */
    private OAuth2RefreshToken generateRefreshToken(OAuth2TokenContext context) {
        Long appId = extractAppId(context);
        Long uid = extractUid(context);

        String tokenValue = bridgeService.issueLegacyAccessToken(appId, uid);

        return new OAuth2RefreshToken(tokenValue, Instant.now());
    }

    /**
     * 从上下文的 {@link org.springframework.security.core.Authentication} 中提取应用 ID。
     * clientId 即 appId 的字符串形式。
     *
     * @param context token 上下文
     * @return 应用 ID
     */
    private Long extractAppId(OAuth2TokenContext context) {
        return Long.valueOf(context.getRegisteredClient().getClientId());
    }

    /**
     * 从认证主体中提取用户 ID（uid）。
     * <p>
     * 初次授权码兑换时，principal 为 {@link UserPrincipal}，通过 {@link UserPrincipal#getId()} 获取；
     * refresh_token 刷新时，合成 principal 的 name 即为 uid 字符串。
     * </p>
     *
     * @param context token 上下文
     * @return 用户 ID
     */
    private Long extractUid(OAuth2TokenContext context) {
        UsernamePasswordAuthenticationToken auth = context.getPrincipal();
        Object principalObj = auth.getPrincipal();
        if (principalObj instanceof UserPrincipal userPrincipal) {
            return userPrincipal.getId();
        }
        return Long.parseLong(auth.getName());
    }

    /**
     * 将 scope 集合合并为空格分隔的字符串。
     *
     * @param scopes scope 集合
     * @return 空格分隔的 scope 字符串
     */
    private String buildScopeString(Set<String> scopes) {
        return String.join(" ", scopes);
    }
}
