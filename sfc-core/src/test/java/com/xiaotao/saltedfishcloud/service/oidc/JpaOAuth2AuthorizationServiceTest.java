package com.xiaotao.saltedfishcloud.service.oidc;

import com.xiaotao.saltedfishcloud.dao.jpa.Oauth2AuthorizationRepo;
import com.xiaotao.saltedfishcloud.model.po.OAuth2AuthorizationEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

/**
 * {@link JpaOAuth2AuthorizationService} 的回归测试。
 * <p>
 * 验证 {@code attributes} 中以 {@link OAuth2AuthorizationRequest} 为值，
 * 经 {@code save → findById} 往返后类型仍为 {@link OAuth2AuthorizationRequest} 而非
 * {@code LinkedHashMap}，防止 Spring AS {@code CodeVerifierAuthenticator}
 * 在 {@code /oauth2/token} 接口再次抛出 {@code ClassCastException}。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class JpaOAuth2AuthorizationServiceTest {

    @Mock
    private Oauth2AuthorizationRepo repo;

    @Mock
    private RegisteredClientRepository registeredClientRepository;

    private JpaOAuth2AuthorizationService service;

    @BeforeEach
    void setUp() {
        service = new JpaOAuth2AuthorizationService(repo, registeredClientRepository);
    }

    /**
     * 验证 {@link OAuth2AuthorizationRequest} 在 {@code save} → {@code findById}
     * 往返后类型保持不变，属性值 (additionalParameters、scopes 等) 完整保留。
     */
    @Test
    void findById_shouldPreserveOAuth2AuthorizationRequestTypeInAttributes() {
        RegisteredClient client = RegisteredClient.withId("client-1")
                .clientId("test-client")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("https://client.example.com/callback")
                .build();
        when(registeredClientRepository.findById("client-1")).thenReturn(client);

        OAuth2AuthorizationRequest authorizationRequest = OAuth2AuthorizationRequest.authorizationCode()
                .authorizationUri("/oauth2/authorize")
                .clientId("test-client")
                .redirectUri("https://client.example.com/callback")
                .scopes(Set.of("openid", "profile"))
                .state("test-state")
                .additionalParameters(Map.of(
                        "code_challenge", "challenge-value",
                        "code_challenge_method", "S256"))
                .build();

        OAuth2Authorization authorization = OAuth2Authorization.withRegisteredClient(client)
                .id("auth-1")
                .principalName("test-user")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .attribute(OAuth2AuthorizationRequest.class.getName(), authorizationRequest)
                .build();

        ArgumentCaptor<OAuth2AuthorizationEntity> entityCaptor = ArgumentCaptor.forClass(OAuth2AuthorizationEntity.class);
        when(repo.save(entityCaptor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        service.save(authorization);

        when(repo.findById("auth-1")).thenReturn(Optional.of(entityCaptor.getValue()));
        OAuth2Authorization result = service.findById("auth-1");

        assertNotNull(result, "反序列化得到的 OAuth2Authorization 不应为 null");
        Object recovered = result.getAttribute(OAuth2AuthorizationRequest.class.getName());
        assertNotNull(recovered, "OAuth2AuthorizationRequest 属性不应丢失");
        assertInstanceOf(OAuth2AuthorizationRequest.class, recovered,
                "attributes 中 OAuth2AuthorizationRequest 应保持原类型，不应退化为 LinkedHashMap");
        OAuth2AuthorizationRequest recoveredRequest = (OAuth2AuthorizationRequest) recovered;
        assertEquals(authorizationRequest.getAuthorizationUri(), recoveredRequest.getAuthorizationUri(),
                "authorizationUri 应在序列化往返后保持一致");
        assertEquals(authorizationRequest.getClientId(), recoveredRequest.getClientId(),
                "clientId 应在序列化往返后保持一致");
        assertEquals(authorizationRequest.getRedirectUri(), recoveredRequest.getRedirectUri(),
                "redirectUri 应在序列化往返后保持一致");
        assertEquals(authorizationRequest.getState(), recoveredRequest.getState(),
                "state 应在序列化往返后保持一致");
        assertEquals(authorizationRequest.getScopes(), recoveredRequest.getScopes(),
                "scopes 应在序列化往返后保持一致");
        assertEquals(authorizationRequest.getAdditionalParameters(), recoveredRequest.getAdditionalParameters(),
                "additionalParameters 应在序列化往返后保持一致 (含 PKCE code_challenge 等)");
    }
}
