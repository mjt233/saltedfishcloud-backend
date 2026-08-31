package com.xiaotao.saltedfishcloud.config.security.oidc;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * {@link PublicClientDeviceAuthenticationProvider} 单元测试。
 */
@ExtendWith(MockitoExtension.class)
class PublicClientDeviceAuthenticationProviderTest {

    @Mock
    private RegisteredClientRepository registeredClientRepository;

    private PublicClientDeviceAuthenticationProvider provider;

    private RegisteredClient publicClient;

    @BeforeEach
    void setUp() {
        provider = new PublicClientDeviceAuthenticationProvider(registeredClientRepository);
        publicClient = RegisteredClient.withId("1")
                .clientId("public-app")
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                .authorizationGrantType(AuthorizationGrantType.DEVICE_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .build();
    }

    /**
     * 合法 public client 的设备码申请应认证成功。
     */
    @Test
    void authenticate_validPublicClient_succeeds() {
        when(registeredClientRepository.findByClientId("public-app")).thenReturn(publicClient);
        OAuth2ClientAuthenticationToken token = new OAuth2ClientAuthenticationToken(
                "public-app",
                ClientAuthenticationMethod.NONE,
                null,
                Map.of()
        );

        Authentication result = provider.authenticate(token);

        assertInstanceOf(OAuth2ClientAuthenticationToken.class, result);
        OAuth2ClientAuthenticationToken authenticated = (OAuth2ClientAuthenticationToken) result;
        assertTrue(authenticated.isAuthenticated());
        assertEquals(publicClient, authenticated.getRegisteredClient());
        assertEquals(ClientAuthenticationMethod.NONE, authenticated.getClientAuthenticationMethod());
    }

    /**
     * 授权码场景应返回 null，交由框架 PublicClientAuthenticationProvider 处理。
     */
    @Test
    void authenticate_authorizationCodeGrant_returnsNull() {
        OAuth2ClientAuthenticationToken token = new OAuth2ClientAuthenticationToken(
                "public-app",
                ClientAuthenticationMethod.NONE,
                null,
                Map.of(OAuth2ParameterNames.GRANT_TYPE, AuthorizationGrantType.AUTHORIZATION_CODE.getValue())
        );

        assertNull(provider.authenticate(token));
    }

    /**
     * 非 NONE 方法返回 null。
     */
    @Test
    void authenticate_clientSecretMethod_returnsNull() {
        OAuth2ClientAuthenticationToken token = new OAuth2ClientAuthenticationToken(
                "conf-app",
                ClientAuthenticationMethod.CLIENT_SECRET_BASIC,
                "secret",
                Map.of()
        );

        assertNull(provider.authenticate(token));
    }

    /**
     * 未知 client_id 抛出 invalid_client。
     */
    @Test
    void authenticate_unknownClient_throwsInvalidClient() {
        when(registeredClientRepository.findByClientId("missing")).thenReturn(null);
        OAuth2ClientAuthenticationToken token = new OAuth2ClientAuthenticationToken(
                "missing",
                ClientAuthenticationMethod.NONE,
                null,
                Map.of()
        );

        OAuth2AuthenticationException ex = assertThrows(
                OAuth2AuthenticationException.class,
                () -> provider.authenticate(token)
        );
        assertEquals(OAuth2ErrorCodes.INVALID_CLIENT, ex.getError().getErrorCode());
    }

    /**
     * 客户端未登记 NONE 方法时抛出 invalid_client。
     */
    @Test
    void authenticate_clientWithoutNoneMethod_throwsInvalidClient() {
        RegisteredClient confidential = RegisteredClient.withId("2")
                .clientId("conf-app")
                .clientSecret("{noop}secret")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.DEVICE_CODE)
                .build();
        when(registeredClientRepository.findByClientId("conf-app")).thenReturn(confidential);

        OAuth2ClientAuthenticationToken token = new OAuth2ClientAuthenticationToken(
                "conf-app",
                ClientAuthenticationMethod.NONE,
                null,
                Map.of()
        );

        OAuth2AuthenticationException ex = assertThrows(
                OAuth2AuthenticationException.class,
                () -> provider.authenticate(token)
        );
        assertEquals(OAuth2ErrorCodes.INVALID_CLIENT, ex.getError().getErrorCode());
    }
}
