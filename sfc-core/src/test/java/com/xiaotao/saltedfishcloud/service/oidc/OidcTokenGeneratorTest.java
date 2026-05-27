package com.xiaotao.saltedfishcloud.service.oidc;

import com.xiaotao.saltedfishcloud.model.po.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.token.JwtGenerator;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenContext;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * {@link OidcTokenGenerator} 的纯单元测试。
 * <p>
 * 验证自定义 Token 生成器的委托行为：
 * <ul>
 *   <li>access_token 类型 → 签发短期 ApiTicket</li>
 *   <li>refresh_token 类型 → 签发长期遗留 Access Token</li>
 *   <li>id_token 类型 → 委托给 Spring 的 {@link JwtGenerator}</li>
 * </ul>
 * </p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OidcTokenGeneratorTest {

    @Mock
    private OidcTokenBridgeService bridgeService;

    @Mock
    private JwtGenerator idTokenGenerator;

    private OidcTokenGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new OidcTokenGenerator(bridgeService, idTokenGenerator);
    }

    /**
     * 验证 {@code access_token} 类型时：
     * <ul>
     *   <li>调用 {@link OidcTokenBridgeService#issueApiTicket(Long, Long, String)}</li>
     *   <li>返回 {@link OAuth2AccessToken}，值为 ApiTicket</li>
     *   <li>有效期约 15 分钟</li>
     * </ul>
     */
    @Test
    void generate_shouldReturnApiTicketAsAccessTokenWith15MinExpiry() {
        OAuth2TokenContext context = buildMockContext(OAuth2TokenType.ACCESS_TOKEN, 100L, 200L,
                Set.of("openid", "profile"), false);
        when(bridgeService.issueApiTicket(eq(100L), eq(200L), any(String.class)))
                .thenReturn("api-ticket-jwt");

        OAuth2Token token = generator.generate(context);

        assertNotNull(token, "access_token 不应为 null");
        assertInstanceOf(OAuth2AccessToken.class, token, "token 类型应为 OAuth2AccessToken");
        assertEquals("api-ticket-jwt", token.getTokenValue());
        assertNotNull(token.getExpiresAt(), "access_token 应有过期时间");
        // 有效期在 14-16 分钟之间
        long ttlSeconds = token.getExpiresAt().getEpochSecond() - token.getIssuedAt().getEpochSecond();
        assertTrue(ttlSeconds >= 14 * 60 && ttlSeconds <= 16 * 60,
                "access_token 有效期应约为 15 分钟，实际为 " + ttlSeconds + " 秒");
    }

    /**
     * 验证 {@code refresh_token} 类型时：
     * <ul>
     *   <li>调用 {@link OidcTokenBridgeService#issueLegacyAccessToken(Long, Long)}</li>
     *   <li>返回 {@link OAuth2RefreshToken}，值为遗留 Access Token</li>
     *   <li>无过期时间（长期有效）</li>
     * </ul>
     */
    @Test
    void generate_shouldReturnLegacyTokenAsRefreshTokenWithNoExpiry() {
        OAuth2TokenContext context = buildMockContext(OAuth2TokenType.REFRESH_TOKEN, 100L, 200L,
                Set.of("openid"), false);
        when(bridgeService.issueLegacyAccessToken(100L, 200L)).thenReturn("legacy-access-token");

        OAuth2Token token = generator.generate(context);

        assertNotNull(token, "refresh_token 不应为 null");
        assertInstanceOf(OAuth2RefreshToken.class, token, "token 类型应为 OAuth2RefreshToken");
        assertEquals("legacy-access-token", token.getTokenValue());
        assertNull(token.getExpiresAt(), "遗留 Access Token 用作 refresh_token 时不应有过期时间（长期有效）");
    }

    /**
     * 验证 {@code id_token} 类型时委托给 {@link JwtGenerator}。
     */
    @Test
    void generate_shouldDelegateIdTokenToJwtGenerator() {
        OAuth2TokenType idTokenType = new OAuth2TokenType(OidcParameterNames.ID_TOKEN);
        OAuth2TokenContext context = buildMockContext(idTokenType, 100L, 200L, Set.of("openid"), false);
        Jwt mockIdToken = mock(Jwt.class);
        when(idTokenGenerator.generate(context)).thenReturn(mockIdToken);

        OAuth2Token token = generator.generate(context);

        assertSame(mockIdToken, token, "id_token 应委托给 JwtGenerator 生成");
    }

    /**
     * 验证未知 token 类型返回 {@code null}。
     */
    @Test
    void generate_shouldReturnNullForUnknownTokenType() {
        OAuth2TokenContext context = buildMockContext(new OAuth2TokenType("unknown"), 100L, 200L,
                Set.of("openid"), false);

        OAuth2Token token = generator.generate(context);

        assertNull(token, "未知 token 类型应返回 null");
    }

    /**
     * 验证从 {@link UserPrincipal} 中提取 uid。
     */
    @Test
    void generate_shouldExtractUidFromUserPrincipal() {
        OAuth2TokenContext context = buildMockContext(OAuth2TokenType.ACCESS_TOKEN, 100L, 200L,
                Set.of("openid"), true); // useUserPrincipal = true
        when(bridgeService.issueApiTicket(eq(100L), eq(200L), any(String.class)))
                .thenReturn("api-ticket");

        OAuth2Token token = generator.generate(context);

        assertNotNull(token);
        verify(bridgeService).issueApiTicket(eq(100L), eq(200L), any(String.class));
    }

    /**
     * 验证从合成 principal（principalName = uid 字符串）中提取 uid，
     * 用于 refresh_token 流程（服务器重启后重建授权对象时的场景）。
     */
    @Test
    void generate_shouldExtractUidFromSyntheticPrincipalName() {
        OAuth2TokenContext context = buildMockContext(OAuth2TokenType.ACCESS_TOKEN, 100L, 200L,
                Set.of("openid"), false); // useUserPrincipal = false → 使用合成 principal
        when(bridgeService.issueApiTicket(eq(100L), eq(200L), any(String.class)))
                .thenReturn("api-ticket");

        OAuth2Token token = generator.generate(context);

        assertNotNull(token);
        verify(bridgeService).issueApiTicket(eq(100L), eq(200L), any(String.class));
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    /**
     * 构建模拟的 {@link OAuth2TokenContext}。
     *
     * @param tokenType      目标 token 类型
     * @param appId          应用 ID（clientId）
     * @param uid            用户 ID
     * @param scopes         已授权的 scope 集合
     * @param useUserPrincipal 是否使用 {@link UserPrincipal}；
     *                         为 {@code false} 时使用以 uid 字符串为 principal name 的合成认证
     * @return 模拟的 token 上下文
     */
    private OAuth2TokenContext buildMockContext(OAuth2TokenType tokenType, Long appId, Long uid,
                                                Set<String> scopes, boolean useUserPrincipal) {
        OAuth2TokenContext context = mock(OAuth2TokenContext.class);
        when(context.getTokenType()).thenReturn(tokenType);

        RegisteredClient registeredClient = mock(RegisteredClient.class);
        when(registeredClient.getClientId()).thenReturn(appId.toString());
        when(context.getRegisteredClient()).thenReturn(registeredClient);

        UsernamePasswordAuthenticationToken principal;
        if (useUserPrincipal) {
            UserPrincipal userPrincipal = new UserPrincipal();
            userPrincipal.setId(uid);
            userPrincipal.setUsername("testuser");
            principal = new UsernamePasswordAuthenticationToken(userPrincipal, null, Collections.emptyList());
        } else {
            // Synthetic principal: principal name is uid string
            principal = new UsernamePasswordAuthenticationToken(uid.toString(), null, Collections.emptyList());
        }
        when(context.getPrincipal()).thenReturn(principal);

        when(context.getAuthorizedScopes()).thenReturn(scopes);

        return context;
    }
}
