package com.xiaotao.saltedfishcloud.service.oidc;

import com.xiaotao.saltedfishcloud.model.vo.ThirdPartyAppAccessTokenPayload;
import com.xiaotao.saltedfishcloud.model.vo.ThirdPartyAppApiTicketPayload;
import com.xiaotao.saltedfishcloud.service.third.ThirdPartyAppApiTicketService;
import com.xiaotao.saltedfishcloud.service.third.ThirdPartyAppTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * {@link OidcTokenBridgeService} 的纯单元测试。
 * <p>
 * 验证 OIDC token bridge 服务对现有 {@link ThirdPartyAppApiTicketService}
 * 与 {@link ThirdPartyAppTokenService} 的委托行为是否正确。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class OidcTokenBridgeServiceTest {

    @Mock
    private ThirdPartyAppApiTicketService apiTicketService;

    @Mock
    private ThirdPartyAppTokenService tokenService;

    private OidcTokenBridgeService bridgeService;

    @BeforeEach
    void setUp() {
        bridgeService = new OidcTokenBridgeService(apiTicketService, tokenService);
    }

    /**
     * 验证签发 ApiTicket 时：
     * <ul>
     *   <li>委托给 {@link ThirdPartyAppApiTicketService#issue(ThirdPartyAppApiTicketPayload, boolean)}</li>
     *   <li>使用 {@code permanent=false}（15 分钟短期票据）</li>
     *   <li>使用 {@code revokeOlder=true}（自动撤销旧票据）</li>
     * </ul>
     */
    @Test
    void issueApiTicket_shouldDelegateToApiTicketServiceWithShortLivedSemantics() {
        when(apiTicketService.issue(any(ThirdPartyAppApiTicketPayload.class), eq(true)))
                .thenReturn("api-ticket-jwt");

        String result = bridgeService.issueApiTicket(100L, 200L, "openid profile");

        assertEquals("api-ticket-jwt", result);

        ArgumentCaptor<ThirdPartyAppApiTicketPayload> captor =
                ArgumentCaptor.forClass(ThirdPartyAppApiTicketPayload.class);
        verify(apiTicketService).issue(captor.capture(), eq(true));

        ThirdPartyAppApiTicketPayload captured = captor.getValue();
        assertEquals(100L, captured.getAppId());
        assertEquals(200L, captured.getUid());
        assertEquals("openid profile", captured.getScope());
        assertFalse(captured.getPermanent(), "ApiTicket 应为短期（permanent=false），作为 access_token 使用");
    }

    /**
     * 验证签发遗留 Access Token 时委托给 {@link ThirdPartyAppTokenService#issueLegacyAccessToken(Long, Long)}。
     */
    @Test
    void issueLegacyAccessToken_shouldDelegateToTokenService() {
        when(tokenService.issueLegacyAccessToken(100L, 200L)).thenReturn("legacy-access-token");

        String result = bridgeService.issueLegacyAccessToken(100L, 200L);

        assertEquals("legacy-access-token", result);
        verify(tokenService).issueLegacyAccessToken(100L, 200L);
    }

    /**
     * 验证验证遗留 Access Token 时委托给 {@link ThirdPartyAppTokenService#validateLegacyAccessToken(String)}。
     */
    @Test
    void validateLegacyAccessToken_shouldDelegateToTokenService() {
        ThirdPartyAppAccessTokenPayload payload = ThirdPartyAppAccessTokenPayload.builder()
                .appId(100L)
                .uid(200L)
                .tokenId("tid-123")
                .build();
        when(tokenService.validateLegacyAccessToken("token-str")).thenReturn(payload);

        ThirdPartyAppAccessTokenPayload result = bridgeService.validateLegacyAccessToken("token-str");

        assertSame(payload, result);
        verify(tokenService).validateLegacyAccessToken("token-str");
    }

    /**
     * 验证撤销令牌时委托给 {@link ThirdPartyAppTokenService#revoke(Long, Long)}。
     */
    @Test
    void revokeTokens_shouldDelegateToTokenService() {
        bridgeService.revokeTokens(100L, 200L);

        verify(tokenService).revoke(100L, 200L);
    }

    /**
     * 验证解析 ApiTicket 时委托给 {@link ThirdPartyAppApiTicketService#parseAndValidateApiTicket(String)}。
     */
    @Test
    void parseApiTicket_shouldDelegateToApiTicketService() {
        ThirdPartyAppApiTicketPayload expected = ThirdPartyAppApiTicketPayload.builder()
                .appId(100L)
                .uid(200L)
                .scope("openid")
                .permanent(false)
                .build();
        when(apiTicketService.parseAndValidateApiTicket("api-ticket")).thenReturn(expected);

        ThirdPartyAppApiTicketPayload result = bridgeService.parseApiTicket("api-ticket");

        assertSame(expected, result);
    }
}
