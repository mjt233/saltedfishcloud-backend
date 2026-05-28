package com.xiaotao.saltedfishcloud.service.oidc;

import com.xiaotao.saltedfishcloud.model.vo.ThirdPartyAppAccessTokenPayload;
import com.xiaotao.saltedfishcloud.model.vo.ThirdPartyAppApiTicketPayload;
import com.xiaotao.saltedfishcloud.service.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

import java.time.Instant;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * {@link OidcAuthorizationService} 的纯单元测试。
 * <p>
 * 验证混合授权服务的委托行为：
 * <ul>
 *   <li>缓存命中时直接返回内存中的授权信息</li>
 *   <li>refresh_token 缓存未命中时，通过遗留 token 服务重建授权信息</li>
 *   <li>无效 refresh_token 时返回 {@code null}</li>
 *   <li>删除操作触发遗留 token 撤销</li>
 * </ul>
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class OidcAuthorizationServiceTest {

    @Mock
    private OAuth2AuthorizationService delegate;

    @Mock
    private OidcTokenBridgeService bridgeService;

    @Mock
    private RegisteredClientRepository registeredClientRepository;

    @Mock
    private UserService userService;

    private OidcAuthorizationService authorizationService;

    @BeforeEach
    void setUp() {
        authorizationService = new OidcAuthorizationService(
                delegate, bridgeService, registeredClientRepository, userService);
    }

    /**
     * 验证 {@code findByToken} 在缓存命中时直接从委托获取结果（auth code 阶段）。
     */
    @Test
    void findByToken_shouldReturnDelegateResultWhenCacheHit() {
        OAuth2Authorization authorization = mock(OAuth2Authorization.class);
        when(delegate.findByToken("refresh-token", OAuth2TokenType.REFRESH_TOKEN))
                .thenReturn(authorization);

        OAuth2Authorization result = authorizationService.findByToken("refresh-token", OAuth2TokenType.REFRESH_TOKEN);

        assertSame(authorization, result);
        verifyNoInteractions(bridgeService);
    }

    /**
     * 验证 {@code findByToken} 在 refresh_token 缓存未命中时，
     * 通过遗留 token 验证重建 {@link OAuth2Authorization}。
     */
    @Test
    void findByToken_refreshToken_shouldFallBackToLegacyValidationWhenCacheMiss() {
        Instant startedAt = Instant.now();
        when(delegate.findByToken("legacy-refresh", OAuth2TokenType.REFRESH_TOKEN)).thenReturn(null);

        ThirdPartyAppAccessTokenPayload payload = ThirdPartyAppAccessTokenPayload.builder()
                .appId(100L)
                .uid(200L)
                .tokenId("token-id-123")
                .build();
        when(bridgeService.validateLegacyAccessToken("legacy-refresh")).thenReturn(payload);

        RegisteredClient registeredClient = mock(RegisteredClient.class);
        when(registeredClientRepository.findByClientId("100")).thenReturn(registeredClient);

        OAuth2Authorization result = authorizationService.findByToken("legacy-refresh", OAuth2TokenType.REFRESH_TOKEN);

        assertNotNull(result, "遗留 refresh_token 应能重建 OAuth2Authorization");
        assertEquals("200", result.getPrincipalName(), "principalName 应为 uid 的字符串形式");
        assertEquals(AuthorizationGrantType.REFRESH_TOKEN, result.getAuthorizationGrantType());

        OAuth2Authorization.Token<OAuth2RefreshToken> refreshToken = result.getRefreshToken();
        assertNotNull(refreshToken, "重建的授权信息中应含有 refresh_token");
        assertEquals("legacy-refresh", refreshToken.getToken().getTokenValue());
        assertNull(refreshToken.getToken().getExpiresAt(), "遗留 refresh_token 不应有过期时间");
        assertFalse(refreshToken.getToken().getIssuedAt().isBefore(startedAt),
                "重建出的 refresh_token issuedAt 应使用当前重建时间，而不是伪造的过去时间");
    }

    /**
     * 验证无效的 refresh_token 返回 {@code null}。
     */
    @Test
    void findByToken_refreshToken_shouldReturnNullForInvalidToken() {
        when(delegate.findByToken("invalid-token", OAuth2TokenType.REFRESH_TOKEN)).thenReturn(null);
        when(bridgeService.validateLegacyAccessToken("invalid-token")).thenThrow(new RuntimeException("invalid token"));

        OAuth2Authorization result = authorizationService.findByToken("invalid-token", OAuth2TokenType.REFRESH_TOKEN);

        assertNull(result, "无效 refresh_token 应返回 null");
    }

    /**
     * 验证 {@code remove} 操作同时：
     * <ul>
     *   <li>调用委托删除内存状态</li>
     *   <li>调用 {@link OidcTokenBridgeService#revokeTokens(Long, Long)} 撤销遗留 token</li>
     * </ul>
     */
    @Test
    void remove_shouldDelegateToBothDelegateAndBridgeRevocation() {
        OAuth2Authorization authorization = mock(OAuth2Authorization.class);
        when(authorization.getPrincipalName()).thenReturn("200");
        RegisteredClient registeredClient = mock(RegisteredClient.class);
        when(registeredClient.getClientId()).thenReturn("100");
        when(authorization.getRegisteredClientId()).thenReturn("reg-id");
        when(registeredClientRepository.findById("reg-id")).thenReturn(registeredClient);

        authorizationService.remove(authorization);

        verify(delegate).remove(authorization);
        verify(bridgeService).revokeTokens(100L, 200L);
    }

    /**
     * 验证 {@code save} 委托给内部 delegate。
     */
    @Test
    void save_shouldDelegateToInternalService() {
        OAuth2Authorization authorization = mock(OAuth2Authorization.class);

        authorizationService.save(authorization);

        verify(delegate).save(authorization);
    }

    /**
     * 验证 {@code findById} 委托给内部 delegate。
     */
    @Test
    void findById_shouldDelegateToInternalService() {
        OAuth2Authorization authorization = mock(OAuth2Authorization.class);
        when(delegate.findById("auth-id")).thenReturn(authorization);

        OAuth2Authorization result = authorizationService.findById("auth-id");

        assertSame(authorization, result);
    }

    /**
     * 验证 {@code findByToken} 在 access_token 缓存未命中时，
     * 通过 ApiTicket 解析重建 {@link OAuth2Authorization}。
     */
    @Test
    void findByToken_accessToken_shouldReconstructFromApiTicket() {
        when(delegate.findByToken("api-ticket", OAuth2TokenType.ACCESS_TOKEN)).thenReturn(null);

        ThirdPartyAppApiTicketPayload payload = ThirdPartyAppApiTicketPayload.builder()
                .appId(100L)
                .uid(200L)
                .scope("openid profile")
                .permanent(false)
                .build();
        when(bridgeService.parseApiTicket("api-ticket")).thenReturn(payload);

        RegisteredClient registeredClient = mock(RegisteredClient.class);
        when(registeredClientRepository.findByClientId("100")).thenReturn(registeredClient);

        OAuth2Authorization result = authorizationService.findByToken("api-ticket", OAuth2TokenType.ACCESS_TOKEN);

        assertNotNull(result, "ApiTicket 应能重建 OAuth2Authorization");
        assertEquals("200", result.getPrincipalName(), "principalName 应为 uid 的字符串形式");
    }

    /**
     * 验证 access_token 重建后的 {@link OAuth2Authorization} 包含正确的授权 scope。
     */
    @Test
    void findByToken_accessToken_shouldContainAuthorizedScopes() {
        when(delegate.findByToken("api-ticket", OAuth2TokenType.ACCESS_TOKEN)).thenReturn(null);

        ThirdPartyAppApiTicketPayload payload = ThirdPartyAppApiTicketPayload.builder()
                .appId(100L)
                .uid(200L)
                .scope("openid profile email")
                .permanent(false)
                .build();
        when(bridgeService.parseApiTicket("api-ticket")).thenReturn(payload);

        RegisteredClient registeredClient = mock(RegisteredClient.class);
        when(registeredClientRepository.findByClientId("100")).thenReturn(registeredClient);

        OAuth2Authorization result = authorizationService.findByToken("api-ticket", OAuth2TokenType.ACCESS_TOKEN);

        assertNotNull(result);
        Set<String> scopes = result.getAuthorizedScopes();
        assertTrue(scopes.contains("openid"), "授权 scope 应包含 openid");
        assertTrue(scopes.contains("profile"), "授权 scope 应包含 profile");
        assertTrue(scopes.contains("email"), "授权 scope 应包含 email");
    }

    /**
     * 验证无效的 access_token（ApiTicket 解析失败）时返回 {@code null}。
     */
    @Test
    void findByToken_accessToken_shouldReturnNullForInvalidApiTicket() {
        when(delegate.findByToken("invalid-ticket", OAuth2TokenType.ACCESS_TOKEN)).thenReturn(null);
        when(bridgeService.parseApiTicket("invalid-ticket")).thenThrow(new RuntimeException("invalid ticket"));

        OAuth2Authorization result = authorizationService.findByToken("invalid-ticket", OAuth2TokenType.ACCESS_TOKEN);

        assertNull(result, "无效 ApiTicket 应返回 null");
    }

    /**
     * 验证重建的 access_token 授权包含 access_token 本身，且 issuedAt 不为 null。
     */
    @Test
    void findByToken_accessToken_shouldContainAccessTokenWithCurrentTimestamp() {
        Instant before = Instant.now();
        when(delegate.findByToken("api-ticket", OAuth2TokenType.ACCESS_TOKEN)).thenReturn(null);

        ThirdPartyAppApiTicketPayload payload = ThirdPartyAppApiTicketPayload.builder()
                .appId(100L)
                .uid(200L)
                .scope("openid")
                .permanent(false)
                .build();
        when(bridgeService.parseApiTicket("api-ticket")).thenReturn(payload);

        RegisteredClient registeredClient = mock(RegisteredClient.class);
        when(registeredClientRepository.findByClientId("100")).thenReturn(registeredClient);

        OAuth2Authorization result = authorizationService.findByToken("api-ticket", OAuth2TokenType.ACCESS_TOKEN);

        assertNotNull(result);
        OAuth2Authorization.Token<OAuth2AccessToken> accessToken = result.getAccessToken();
        assertNotNull(accessToken, "重建的授权信息中应含有 access_token");
        assertEquals("api-ticket", accessToken.getToken().getTokenValue());
        assertFalse(accessToken.getToken().getIssuedAt().isBefore(before),
                "access_token issuedAt 应使用当前重建时间");
    }

    /**
     * 验证当 {@code tokenType=null}（标准 revocation/introspection 路径）时，
     * 服务仍能从遗留 refresh_token 重建授权信息。
     */
    @Test
    void findByToken_nullTokenType_shouldReconstructFromLegacyRefreshToken() {
        when(delegate.findByToken("legacy-refresh", null)).thenReturn(null);

        ThirdPartyAppAccessTokenPayload payload = ThirdPartyAppAccessTokenPayload.builder()
                .appId(100L)
                .uid(200L)
                .tokenId("token-id-123")
                .build();
        when(bridgeService.validateLegacyAccessToken("legacy-refresh")).thenReturn(payload);

        RegisteredClient registeredClient = mock(RegisteredClient.class);
        when(registeredClientRepository.findByClientId("100")).thenReturn(registeredClient);

        OAuth2Authorization result = authorizationService.findByToken("legacy-refresh", null);

        assertNotNull(result, "tokenType=null 时应能从遗留 refresh_token 重建授权");
        assertNotNull(result.getRefreshToken(), "重建结果中应包含 refresh_token");
        verify(bridgeService).validateLegacyAccessToken("legacy-refresh");
    }

    /**
     * 验证当 {@code tokenType=null} 且遗留 refresh_token 校验失败时，
     * 服务会继续尝试按 ApiTicket 重建 access_token 授权信息。
     */
    @Test
    void findByToken_nullTokenType_shouldFallBackToApiTicketWhenRefreshLookupFails() {
        when(delegate.findByToken("api-ticket", null)).thenReturn(null);
        when(bridgeService.validateLegacyAccessToken("api-ticket")).thenThrow(new RuntimeException("not refresh token"));

        ThirdPartyAppApiTicketPayload payload = ThirdPartyAppApiTicketPayload.builder()
                .appId(100L)
                .uid(200L)
                .scope("openid profile")
                .permanent(false)
                .build();
        when(bridgeService.parseApiTicket("api-ticket")).thenReturn(payload);

        RegisteredClient registeredClient = mock(RegisteredClient.class);
        when(registeredClientRepository.findByClientId("100")).thenReturn(registeredClient);

        OAuth2Authorization result = authorizationService.findByToken("api-ticket", null);

        assertNotNull(result, "tokenType=null 时应能继续从 ApiTicket 重建授权");
        assertNotNull(result.getAccessToken(), "重建结果中应包含 access_token");
        verify(bridgeService).parseApiTicket("api-ticket");
    }
}
