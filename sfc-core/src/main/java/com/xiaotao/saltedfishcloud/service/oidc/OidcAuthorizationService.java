package com.xiaotao.saltedfishcloud.service.oidc;

import com.xiaotao.saltedfishcloud.model.vo.ThirdPartyAppAccessTokenPayload;
import com.xiaotao.saltedfishcloud.model.vo.ThirdPartyAppApiTicketPayload;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.InMemoryOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

import java.security.Principal;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 混合式 OIDC 授权服务。
 * <p>
 * 采用两级查找策略：
 * <ol>
 *   <li>优先从内存委托（{@link InMemoryOAuth2AuthorizationService}）中获取，
 *       适用于 auth code 兑换阶段的短生命周期授权状态。</li>
 *   <li>若内存未命中且 token 类型为 {@link OAuth2TokenType#REFRESH_TOKEN}，
 *       则通过 {@link OidcTokenBridgeService#validateLegacyAccessToken(String)} 验证遗留 token，
 *       并重建 {@link OAuth2Authorization} 以支持服务重启后的 refresh_token 流程。</li>
 *   <li>若内存未命中且 token 类型为 {@link OAuth2TokenType#ACCESS_TOKEN}，
 *       则通过 {@link OidcTokenBridgeService#parseApiTicket(String)} 解析 ApiTicket，
 *       重建 {@link OAuth2Authorization} 以支持遗留 ApiTicket 的 OIDC UserInfo 查询。</li>
 * </ol>
 * </p>
 * <p>删除操作同时触发内存委托删除与遗留 token 撤销。</p>
 */
@Slf4j
public class OidcAuthorizationService implements OAuth2AuthorizationService {

    private final OAuth2AuthorizationService delegate;
    private final OidcTokenBridgeService bridgeService;
    private final RegisteredClientRepository registeredClientRepository;

    /**
     * 构造混合式授权服务。
     *
     * @param delegate                    内存授权委托（通常为 {@link InMemoryOAuth2AuthorizationService}）
     * @param bridgeService               OIDC token 桥接服务
     * @param registeredClientRepository  注册客户端仓库，用于重建 {@link OAuth2Authorization}
     */
    public OidcAuthorizationService(OAuth2AuthorizationService delegate,
                                    OidcTokenBridgeService bridgeService,
                                    RegisteredClientRepository registeredClientRepository) {
        this.delegate = delegate;
        this.bridgeService = bridgeService;
        this.registeredClientRepository = registeredClientRepository;
    }

    /**
     * {@inheritDoc}
     * <p>委托给内存委托服务保存授权信息。</p>
     */
    @Override
    public void save(OAuth2Authorization authorization) {
        delegate.save(authorization);
    }

    /**
     * {@inheritDoc}
     * <p>
     * 同时触发：
     * <ol>
     *   <li>内存委托删除（移除 auth code 阶段的短生命周期状态）</li>
     *   <li>遗留 token 撤销（通过 {@link OidcTokenBridgeService#revokeTokens(Long, Long)}）</li>
     * </ol>
     * </p>
     */
    @Override
    public void remove(OAuth2Authorization authorization) {
        delegate.remove(authorization);
        try {
            Long uid = Long.parseLong(authorization.getPrincipalName());
            RegisteredClient client = registeredClientRepository.findById(authorization.getRegisteredClientId());
            if (client != null) {
                Long appId = Long.parseLong(client.getClientId());
                bridgeService.revokeTokens(appId, uid);
            }
        } catch (Exception e) {
            log.warn("撤销遗留 token 时发生错误，authorization id={}", authorization.getId(), e);
        }
    }

    /**
     * {@inheritDoc}
     * <p>委托给内存委托服务按 ID 查找授权信息。</p>
     */
    @Override
    public OAuth2Authorization findById(String id) {
        return delegate.findById(id);
    }

    /**
     * {@inheritDoc}
     * <p>
     * 查找策略：
     * <ol>
     *   <li>优先从内存委托中查找。</li>
     *   <li>若为 {@link OAuth2TokenType#REFRESH_TOKEN} 类型且内存未命中，
     *       尝试通过遗留 token 验证重建 {@link OAuth2Authorization}。</li>
     *   <li>若为 {@link OAuth2TokenType#ACCESS_TOKEN} 类型且内存未命中，
     *       尝试通过 ApiTicket 解析重建 {@link OAuth2Authorization}。</li>
     * </ol>
     * </p>
     *
     * @param token     token 字符串
     * @param tokenType token 类型
     * @return 对应的 {@link OAuth2Authorization}，未找到时返回 {@code null}
     */
    @Override
    public OAuth2Authorization findByToken(String token, OAuth2TokenType tokenType) {
        OAuth2Authorization authorization = delegate.findByToken(token, tokenType);
        if (authorization != null) {
            return authorization;
        }

        if (tokenType == null) {
            OAuth2Authorization refreshAuthorization = reconstructFromLegacyRefreshToken(token);
            if (refreshAuthorization != null) {
                return refreshAuthorization;
            }
            return reconstructFromApiTicket(token);
        }

        if (OAuth2TokenType.REFRESH_TOKEN.equals(tokenType)) {
            return reconstructFromLegacyRefreshToken(token);
        }

        if (OAuth2TokenType.ACCESS_TOKEN.equals(tokenType)) {
            return reconstructFromApiTicket(token);
        }
        return null;
    }

    /**
     * 通过遗留 Access Token 重建 {@link OAuth2Authorization}。
     * <p>
     * 解析 token 的 JWT 载荷以获取 appId 和 uid，然后构建一个授权对象，
     * 使其满足 Spring Authorization Server refresh_token 流程的要求。
     * </p>
     *
     * @param token 遗留格式的 refresh_token 字符串
     * @return 重建的 {@link OAuth2Authorization}，解析失败时返回 {@code null}
     */
    private OAuth2Authorization reconstructFromLegacyRefreshToken(String token) {
        try {
            ThirdPartyAppAccessTokenPayload payload = bridgeService.validateLegacyAccessToken(token);
            String clientId = payload.getAppId().toString();
            RegisteredClient client = registeredClientRepository.findByClientId(clientId);
            if (client == null) {
                return null;
            }

            String principalName = payload.getUid().toString();
            UsernamePasswordAuthenticationToken syntheticAuth = new UsernamePasswordAuthenticationToken(
                    principalName, null, Collections.emptyList());

            return OAuth2Authorization.withRegisteredClient(client)
                    .id(payload.getTokenId())
                    .principalName(principalName)
                    .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                    .attribute(Principal.class.getName(), syntheticAuth)
                    .refreshToken(new OAuth2RefreshToken(token, Instant.now()))
                    .build();
        } catch (Exception e) {
            log.debug("遗留 refresh_token 验证失败：{}", e.getMessage());
            return null;
        }
    }

    /**
     * 通过 ApiTicket 重建 {@link OAuth2Authorization}，用于支持遗留 ApiTicket 的 OIDC UserInfo 查询。
     * <p>
     * 解析 ApiTicket 载荷以获取 appId、uid 与 scope，然后构建一个包含 access_token 的授权对象，
     * 使 Spring Authorization Server 能够从中提取 principalName 和授权 scope 用于 userinfo 端点。
     * </p>
     * <p>
     * 注意：重建的授权对象不会持久化，ID 每次生成新的随机值。
     * </p>
     *
     * @param token ApiTicket 字符串（遗留 access_token）
     * @return 重建的 {@link OAuth2Authorization}，解析失败时返回 {@code null}
     */
    private OAuth2Authorization reconstructFromApiTicket(String token) {
        try {
            ThirdPartyAppApiTicketPayload payload = bridgeService.parseApiTicket(token);
            String clientId = payload.getAppId().toString();
            RegisteredClient client = registeredClientRepository.findByClientId(clientId);
            if (client == null) {
                return null;
            }

            String principalName = payload.getUid().toString();
            Set<String> scopes = parseScopes(payload.getScope());
            Instant now = Instant.now();
            UsernamePasswordAuthenticationToken syntheticAuth = new UsernamePasswordAuthenticationToken(
                    principalName, null, Collections.emptyList());

            return OAuth2Authorization.withRegisteredClient(client)
                    .id(UUID.randomUUID().toString())
                    .principalName(principalName)
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .authorizedScopes(scopes)
                    .attribute(Principal.class.getName(), syntheticAuth)
                    .accessToken(new OAuth2AccessToken(
                            OAuth2AccessToken.TokenType.BEARER, token, now, now.plusSeconds(900)))
                    .build();
        } catch (Exception e) {
            log.debug("ApiTicket 解析失败：{}", e.getMessage());
            return null;
        }
    }

    /**
     * 将空格分隔的 scope 字符串拆分为 {@link Set}。
     *
     * @param scope 空格分隔的 scope 字符串，允许为 {@code null} 或空白
     * @return scope 集合；输入为空时返回空集合
     */
    private static Set<String> parseScopes(String scope) {
        if (scope == null || scope.isBlank()) {
            return Collections.emptySet();
        }
        return new HashSet<>(Arrays.stream(scope.trim().split("\\s+")).collect(Collectors.toSet()));
    }
}
