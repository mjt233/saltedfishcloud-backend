package com.xiaotao.saltedfishcloud.service;

import com.xiaotao.saltedfishcloud.cache.CacheService;
import com.xiaotao.saltedfishcloud.dao.jpa.ThirdPartyAppTokenRepo;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.model.po.ThirdPartyAppToken;
import com.xiaotao.saltedfishcloud.service.third.ThirdPartyAppApiTicketService;
import com.xiaotao.saltedfishcloud.service.third.ThirdPartyAppAuthorizationService;
import com.xiaotao.saltedfishcloud.service.third.ThirdPartyAppKeyService;
import com.xiaotao.saltedfishcloud.service.third.ThirdPartyAppService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ThirdPartyAppTokenServiceImpl} 的单元测试。
 */
@ExtendWith(MockitoExtension.class)
class ThirdPartyAppTokenServiceImplTest {

    @Mock
    private ThirdPartyAppAuthorizationService authorizationService;

    @Mock
    private ThirdPartyAppService appService;

    @Mock
    private ThirdPartyAppKeyService keyService;

    @Mock
    private CacheService cacheService;

    @Mock
    private ThirdPartyAppApiTicketService apiTicketService;

    @Mock
    private ThirdPartyAppTokenRepo tokenRepo;

    private ThirdPartyAppTokenServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ThirdPartyAppTokenServiceImpl(
                authorizationService,
                appService,
                keyService,
                cacheService,
                apiTicketService
        );
        service.setRepository(tokenRepo);
    }

    /**
     * 验证签发遗留 Access Token 后，若持久化记录仍存在，则后续校验可以成功解析载荷。
     */
    @Test
    void validateLegacyAccessToken_shouldReturnPayloadWhenPersistedRecordMatches() {
        when(tokenRepo.findByAppIdAndUid(100L, 200L)).thenReturn(null);
        when(tokenRepo.save(any(ThirdPartyAppToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        String token = service.issueLegacyAccessToken(100L, 200L);
        ArgumentCaptor<ThirdPartyAppToken> captor = ArgumentCaptor.forClass(ThirdPartyAppToken.class);
        verify(tokenRepo).save(captor.capture());

        ThirdPartyAppToken persistedRecord = captor.getValue();
        assertNotNull(persistedRecord.getAccessToken(), "签发后应持久化 Access Token 指纹");

        when(tokenRepo.findByAppIdAndUid(100L, 200L)).thenReturn(persistedRecord);

        assertEquals(100L, service.validateLegacyAccessToken(token).getAppId());
        assertEquals(200L, service.validateLegacyAccessToken(token).getUid());
    }

    /**
     * 验证遗留 Access Token 在签发后若对应持久化记录已不存在，则必须判定为无效，
     * 防止已撤销的 refresh_token 仅凭 JWT 结构仍可继续使用。
     */
    @Test
    void validateLegacyAccessToken_shouldRejectTokenWhenPersistedRecordIsMissingAfterIssuance() {
        when(tokenRepo.findByAppIdAndUid(100L, 200L)).thenReturn(null);
        when(tokenRepo.save(any(ThirdPartyAppToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        String token = service.issueLegacyAccessToken(100L, 200L);
        verify(tokenRepo).save(any(ThirdPartyAppToken.class));

        when(tokenRepo.findByAppIdAndUid(100L, 200L)).thenReturn(null);

        assertThrows(JsonException.class, () -> service.validateLegacyAccessToken(token));
    }
}
