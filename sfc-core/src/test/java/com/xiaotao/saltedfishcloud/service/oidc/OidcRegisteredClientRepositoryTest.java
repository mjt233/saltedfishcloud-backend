package com.xiaotao.saltedfishcloud.service.oidc;

import com.xiaotao.saltedfishcloud.dao.jpa.ThirdPartyAppKeyRepo;
import com.xiaotao.saltedfishcloud.dao.jpa.ThirdPartyAppRedirectUriRepo;
import com.xiaotao.saltedfishcloud.enums.OidcTokenEndpointAuthMethod;
import com.xiaotao.saltedfishcloud.model.po.ThirdPartyApp;
import com.xiaotao.saltedfishcloud.model.po.ThirdPartyAppAuthorization;
import com.xiaotao.saltedfishcloud.model.po.ThirdPartyAppKey;
import com.xiaotao.saltedfishcloud.model.po.ThirdPartyAppRedirectUri;
import com.xiaotao.saltedfishcloud.model.vo.ThirdPartyAppUserAuthorizationVo;
import com.xiaotao.saltedfishcloud.service.third.ThirdPartyAppAuthorizationService;
import com.xiaotao.saltedfishcloud.service.third.ThirdPartyAppService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsent;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * {@link OidcRegisteredClientRepository} 与 {@link OidcAuthorizationConsentService} 的纯单元测试。
 * <p>
 * 使用 Mockito 模拟依赖，不依赖 Spring 上下文，验证：
 * <ul>
 *   <li>OIDC 客户端映射逻辑（元数据、重定向 URI、认证方式、PKCE）</li>
 *   <li>非 OIDC 或已禁用的应用不暴露为注册客户端</li>
 *   <li>授权同意的持久化与撤销逻辑</li>
 * </ul>
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class OidcRegisteredClientRepositoryTest {

    @Mock
    private ThirdPartyAppService appService;

    @Mock
    private ThirdPartyAppKeyRepo keyRepo;

    @Mock
    private ThirdPartyAppRedirectUriRepo redirectUriRepo;

    @Mock
    private ThirdPartyAppAuthorizationService authorizationService;

    private OidcRegisteredClientRepository clientRepository;
    private OidcAuthorizationConsentService consentService;

    @BeforeEach
    void setUp() {
        clientRepository = new OidcRegisteredClientRepository(appService, keyRepo, redirectUriRepo);
        consentService = new OidcAuthorizationConsentService(authorizationService);
    }

    // -------------------------------------------------------------------------
    // OidcRegisteredClientRepository - findByClientId
    // -------------------------------------------------------------------------

    /**
     * 验证 OIDC 已启用的应用能被正确映射为 {@link RegisteredClient}，
     * 包括 clientId、clientSecret、重定向 URI 和支持的授权类型。
     */
    @Test
    void findByClientId_shouldMapOidcEnabledAppToRegisteredClient() {
        // Given
        ThirdPartyApp app = buildOidcApp(42L, true, true, OidcTokenEndpointAuthMethod.CLIENT_SECRET_BASIC, false);

        ThirdPartyAppKey key = new ThirdPartyAppKey();
        key.setClientSecretHash("hashed-secret");

        ThirdPartyAppRedirectUri uri = new ThirdPartyAppRedirectUri();
        uri.setUri("https://client.example.com/callback");

        when(appService.findById(42L)).thenReturn(app);
        when(keyRepo.findByAppId(42L)).thenReturn(List.of(key));
        when(redirectUriRepo.findByAppId(42L)).thenReturn(List.of(uri));

        // When
        RegisteredClient client = clientRepository.findByClientId("42");

        // Then
        assertNotNull(client, "OIDC 已启用的应用应映射为 RegisteredClient");
        assertEquals("42", client.getClientId());
        assertEquals("42", client.getId());
        assertEquals("hashed-secret", client.getClientSecret());
        assertThat(client.getRedirectUris()).containsExactly("https://client.example.com/callback");
        assertThat(client.getScopes()).contains(OidcScopes.OPENID, "profile");
    }

    /**
     * 验证 {@link ThirdPartyApp#getOidcEnabled()} 为 false 时，
     * {@code findByClientId} 返回 null，不暴露为注册客户端。
     */
    @Test
    void findByClientId_shouldReturnNullWhenOidcIsDisabled() {
        // Given — oidcEnabled=false
        ThirdPartyApp app = buildOidcApp(10L, true, false, OidcTokenEndpointAuthMethod.CLIENT_SECRET_BASIC, false);
        when(appService.findById(10L)).thenReturn(app);

        // When
        RegisteredClient client = clientRepository.findByClientId("10");

        // Then
        assertNull(client, "oidcEnabled=false 的应用不应暴露为注册客户端");
        verify(keyRepo, never()).findByAppId(any());
    }

    /**
     * 验证应用不存在时，{@code findByClientId} 返回 null。
     */
    @Test
    void findByClientId_shouldReturnNullWhenAppNotFound() {
        // Given
        when(appService.findById(99L)).thenReturn(null);

        // When
        RegisteredClient client = clientRepository.findByClientId("99");

        // Then
        assertNull(client, "不存在的应用不应暴露为注册客户端");
    }

    /**
     * 验证应用已禁用（{@link ThirdPartyApp#getIsEnabled()} 为 false）时，
     * {@code findByClientId} 返回 null。
     */
    @Test
    void findByClientId_shouldReturnNullWhenAppIsDisabled() {
        // Given — isEnabled=false
        ThirdPartyApp app = buildOidcApp(11L, false, true, OidcTokenEndpointAuthMethod.CLIENT_SECRET_BASIC, false);
        when(appService.findById(11L)).thenReturn(app);

        // When
        RegisteredClient client = clientRepository.findByClientId("11");

        // Then
        assertNull(client, "已禁用的应用不应暴露为注册客户端");
    }

    /**
     * 验证认证方式映射：{@link OidcTokenEndpointAuthMethod#CLIENT_SECRET_POST}
     * 应映射为 {@link ClientAuthenticationMethod#CLIENT_SECRET_POST}。
     */
    @Test
    void findByClientId_shouldMapClientSecretPostAuthMethod() {
        // Given
        ThirdPartyApp app = buildOidcApp(20L, true, true, OidcTokenEndpointAuthMethod.CLIENT_SECRET_POST, false);
        when(appService.findById(20L)).thenReturn(app);
        when(keyRepo.findByAppId(20L)).thenReturn(Collections.emptyList());
        when(redirectUriRepo.findByAppId(20L)).thenReturn(List.of(buildRedirectUri(20L, "https://example.com/cb")));

        // When
        RegisteredClient client = clientRepository.findByClientId("20");

        // Then
        assertNotNull(client);
        assertThat(client.getClientAuthenticationMethods())
                .contains(ClientAuthenticationMethod.CLIENT_SECRET_POST);
    }

    /**
     * 验证认证方式映射：{@link OidcTokenEndpointAuthMethod#NONE}
     * 应映射为 {@link ClientAuthenticationMethod#NONE}。
     */
    @Test
    void findByClientId_shouldMapNoneAuthMethod() {
        // Given
        ThirdPartyApp app = buildOidcApp(21L, true, true, OidcTokenEndpointAuthMethod.NONE, false);
        when(appService.findById(21L)).thenReturn(app);
        when(keyRepo.findByAppId(21L)).thenReturn(Collections.emptyList());
        when(redirectUriRepo.findByAppId(21L)).thenReturn(List.of(buildRedirectUri(21L, "https://example.com/cb")));

        // When
        RegisteredClient client = clientRepository.findByClientId("21");

        // Then
        assertNotNull(client);
        assertThat(client.getClientAuthenticationMethods())
                .contains(ClientAuthenticationMethod.NONE);
    }

    /**
     * 验证当 {@link ThirdPartyApp#getRequirePkce()} 为 true 时，
     * {@link RegisteredClient} 的 ClientSettings 中 requireProofKey 设为 true。
     */
    @Test
    void findByClientId_shouldSetRequireProofKeyWhenPkceIsRequired() {
        // Given
        ThirdPartyApp app = buildOidcApp(30L, true, true, OidcTokenEndpointAuthMethod.NONE, true);
        when(appService.findById(30L)).thenReturn(app);
        when(keyRepo.findByAppId(30L)).thenReturn(Collections.emptyList());
        when(redirectUriRepo.findByAppId(30L)).thenReturn(List.of(buildRedirectUri(30L, "https://example.com/cb")));

        // When
        RegisteredClient client = clientRepository.findByClientId("30");

        // Then
        assertNotNull(client);
        assertTrue(client.getClientSettings().isRequireProofKey(), "PKCE 要求应映射到 requireProofKey");
    }

    /**
     * 验证 OIDC 已启用的应用默认支持设备授权模式，以便标准客户端可使用 {@code device_code} 流程。
     */
    @Test
    void findByClientId_shouldEnableDeviceCodeGrantForOidcClient() {
        // Given
        ThirdPartyApp app = buildOidcApp(31L, true, true, OidcTokenEndpointAuthMethod.CLIENT_SECRET_BASIC, false);
        when(appService.findById(31L)).thenReturn(app);
        when(keyRepo.findByAppId(31L)).thenReturn(Collections.emptyList());
        when(redirectUriRepo.findByAppId(31L)).thenReturn(List.of(buildRedirectUri(31L, "https://example.com/cb")));

        // When
        RegisteredClient client = clientRepository.findByClientId("31");

        // Then
        assertNotNull(client);
        assertThat(client.getAuthorizationGrantTypes()).contains(AuthorizationGrantType.DEVICE_CODE);
    }

    /**
     * 验证 {@code findById} 与 {@code findByClientId} 行为一致，
     * 均能通过应用 ID 找到对应的注册客户端。
     */
    @Test
    void findById_shouldDelegateToFindByClientId() {
        // Given
        ThirdPartyApp app = buildOidcApp(42L, true, true, OidcTokenEndpointAuthMethod.CLIENT_SECRET_BASIC, false);
        when(appService.findById(42L)).thenReturn(app);
        when(keyRepo.findByAppId(42L)).thenReturn(Collections.emptyList());
        when(redirectUriRepo.findByAppId(42L)).thenReturn(List.of(buildRedirectUri(42L, "https://example.com/cb")));

        // When
        RegisteredClient client = clientRepository.findById("42");

        // Then
        assertNotNull(client, "findById 应能通过内部 ID 找到注册客户端");
        assertEquals("42", client.getId());
    }

    // -------------------------------------------------------------------------
    // OidcAuthorizationConsentService - save
    // -------------------------------------------------------------------------

    /**
     * 验证 {@link OidcAuthorizationConsentService#save(OAuth2AuthorizationConsent)} 将
     * consent 中的 SCOPE_ 授权提取为空格分隔的 scope 字符串并调用授权服务。
     */
    @Test
    void consentService_save_shouldExtractScopesAndCallAuthorize() {
        // Given
        OAuth2AuthorizationConsent consent = OAuth2AuthorizationConsent
                .withId("100", "200")
                .scope("openid")
                .scope("profile")
                .build();

        // When
        consentService.save(consent);

        // Then — 必须调用 authorize，且 scope 包含两个作用域
        verify(authorizationService).authorize(eq(100L), eq(200L), argThat(scope -> {
            String[] parts = scope.split(" ");
            assertThat(parts).containsExactlyInAnyOrder("openid", "profile");
            return true;
        }));
    }

    /**
     * 验证当 consent 中不存在任何 scope 时，保存逻辑会撤销原授权而不是写入空 scope 记录。
     */
    @Test
    void consentService_save_shouldRevokeWhenConsentDoesNotContainAnyScopes() {
        // Given
        OAuth2AuthorizationConsent consent = OAuth2AuthorizationConsent
                .withId("100", "200")
                .authority(new SimpleGrantedAuthority("SCOPE_"))
                .build();

        // When
        consentService.save(consent);

        // Then
        verify(authorizationService).revoke(100L, 200L);
        verify(authorizationService, never()).authorize(anyLong(), anyLong(), anyString());
    }

    /**
     * 验证 {@link OidcAuthorizationConsentService#remove(OAuth2AuthorizationConsent)} 调用
     * 授权服务的 revoke 方法撤销授权。
     */
    @Test
    void consentService_remove_shouldCallRevoke() {
        // Given
        OAuth2AuthorizationConsent consent = OAuth2AuthorizationConsent
                .withId("100", "200")
                .scope("openid")
                .build();

        // When
        consentService.remove(consent);

        // Then
        verify(authorizationService).revoke(100L, 200L);
    }

    /**
     * 验证 {@link OidcAuthorizationConsentService#findById(String, String)} 在存在授权记录时
     * 返回正确映射的 {@link OAuth2AuthorizationConsent}，包含正确的 scope 权限。
     */
    @Test
    void consentService_findById_shouldReturnConsentWhenAuthorizationExists() {
        // Given
        ThirdPartyAppAuthorization authorization = new ThirdPartyAppAuthorization();
        authorization.setAppId(100L);
        authorization.setUid(200L);
        authorization.setScope("openid profile");

        ThirdPartyAppUserAuthorizationVo vo = ThirdPartyAppUserAuthorizationVo.builder()
                .authorization(authorization)
                .build();
        when(authorizationService.getUserAppAuthorization(100L, 200L)).thenReturn(vo);

        // When
        OAuth2AuthorizationConsent found = consentService.findById("100", "200");

        // Then
        assertNotNull(found, "有授权记录时应返回 OAuth2AuthorizationConsent");
        assertEquals("100", found.getRegisteredClientId());
        assertEquals("200", found.getPrincipalName());
        assertThat(found.getAuthorities())
                .extracting("authority")
                .containsExactlyInAnyOrder("SCOPE_openid", "SCOPE_profile");
    }

    /**
     * 验证 {@link OidcAuthorizationConsentService#findById(String, String)} 在没有授权记录时
     * 返回 null。
     */
    @Test
    void consentService_findById_shouldReturnNullWhenNoAuthorization() {
        // Given
        ThirdPartyAppUserAuthorizationVo vo = ThirdPartyAppUserAuthorizationVo.builder()
                .authorization(null)
                .build();
        when(authorizationService.getUserAppAuthorization(100L, 200L)).thenReturn(vo);

        // When
        OAuth2AuthorizationConsent found = consentService.findById("100", "200");

        // Then
        assertNull(found, "没有授权记录时应返回 null");
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    /**
     * 构建测试用的 {@link ThirdPartyApp} 实例。
     *
     * @param id          应用 ID
     * @param isEnabled   是否启用
     * @param oidcEnabled 是否启用 OIDC
     * @param authMethod  Token 端点认证方式
     * @param requirePkce 是否要求 PKCE
     * @return 配置好的应用对象
     */
    private ThirdPartyApp buildOidcApp(Long id, boolean isEnabled, boolean oidcEnabled,
                                       OidcTokenEndpointAuthMethod authMethod, boolean requirePkce) {
        ThirdPartyApp app = new ThirdPartyApp();
        app.setId(id);
        app.setName("test-app-" + id);
        app.setIsEnabled(isEnabled);
        app.setOidcEnabled(oidcEnabled);
        app.setOidcTokenEndpointAuthMethod(authMethod);
        app.setRequirePkce(requirePkce);
        return app;
    }

    /**
     * 构建测试用的 {@link ThirdPartyAppRedirectUri} 实例。
     *
     * @param appId 所属应用 ID
     * @param uri   重定向 URI
     * @return 配置好的重定向 URI 对象
     */
    private ThirdPartyAppRedirectUri buildRedirectUri(Long appId, String uri) {
        ThirdPartyAppRedirectUri redirectUri = new ThirdPartyAppRedirectUri();
        redirectUri.setAppId(appId);
        redirectUri.setUri(uri);
        return redirectUri;
    }
}
