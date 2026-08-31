package com.xiaotao.saltedfishcloud.config.security.oidc;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link PublicClientDeviceAuthenticationConverter} 单元测试。
 */
class PublicClientDeviceAuthenticationConverterTest {

    private final PublicClientDeviceAuthenticationConverter converter =
            new PublicClientDeviceAuthenticationConverter();

    /**
     * 设备码申请（无 grant_type，仅 client_id）应转换为 NONE 客户端认证令牌。
     */
    @Test
    void convert_deviceAuthorizationRequest_returnsNoneClientToken() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/oauth2/device_authorization");
        request.setParameter(OAuth2ParameterNames.CLIENT_ID, "public-app");
        request.setParameter(OAuth2ParameterNames.SCOPE, "profile");
        request.setParameter("code_challenge", "challenge");
        request.setParameter("code_challenge_method", "S256");

        Authentication authentication = converter.convert(request);

        assertInstanceOf(OAuth2ClientAuthenticationToken.class, authentication);
        OAuth2ClientAuthenticationToken clientAuth = (OAuth2ClientAuthenticationToken) authentication;
        assertEquals("public-app", clientAuth.getPrincipal().toString());
        assertEquals(ClientAuthenticationMethod.NONE, clientAuth.getClientAuthenticationMethod());
        assertFalse(clientAuth.isAuthenticated());
        assertEquals("profile", clientAuth.getAdditionalParameters().get(OAuth2ParameterNames.SCOPE));
    }

    /**
     * 设备码换票请求应转换为 NONE 客户端认证令牌。
     */
    @Test
    void convert_deviceCodeTokenRequest_returnsNoneClientToken() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/oauth2/token");
        request.setParameter(OAuth2ParameterNames.CLIENT_ID, "public-app");
        request.setParameter(OAuth2ParameterNames.GRANT_TYPE, AuthorizationGrantType.DEVICE_CODE.getValue());
        request.setParameter("device_code", "dc-1");
        request.setParameter("code_verifier", "verifier");

        Authentication authentication = converter.convert(request);

        assertInstanceOf(OAuth2ClientAuthenticationToken.class, authentication);
        OAuth2ClientAuthenticationToken clientAuth = (OAuth2ClientAuthenticationToken) authentication;
        assertEquals(ClientAuthenticationMethod.NONE, clientAuth.getClientAuthenticationMethod());
        assertEquals(
                AuthorizationGrantType.DEVICE_CODE.getValue(),
                clientAuth.getAdditionalParameters().get(OAuth2ParameterNames.GRANT_TYPE)
        );
        assertEquals("verifier", clientAuth.getAdditionalParameters().get("code_verifier"));
    }

    /**
     * public client 的 refresh_token 请求应转换为 NONE 客户端认证令牌。
     */
    @Test
    void convert_refreshTokenRequest_returnsNoneClientToken() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/oauth2/token");
        request.setParameter(OAuth2ParameterNames.CLIENT_ID, "public-app");
        request.setParameter(OAuth2ParameterNames.GRANT_TYPE, AuthorizationGrantType.REFRESH_TOKEN.getValue());
        request.setParameter(OAuth2ParameterNames.REFRESH_TOKEN, "rt-1");

        Authentication authentication = converter.convert(request);

        assertInstanceOf(OAuth2ClientAuthenticationToken.class, authentication);
        assertEquals(
                ClientAuthenticationMethod.NONE,
                ((OAuth2ClientAuthenticationToken) authentication).getClientAuthenticationMethod()
        );
    }

    /**
     * 授权码请求应由框架内置 PublicClientAuthenticationConverter 处理，本转换器返回 null。
     */
    @Test
    void convert_authorizationCodeRequest_returnsNull() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/oauth2/token");
        request.setParameter(OAuth2ParameterNames.CLIENT_ID, "public-app");
        request.setParameter(OAuth2ParameterNames.GRANT_TYPE, AuthorizationGrantType.AUTHORIZATION_CODE.getValue());
        request.setParameter(OAuth2ParameterNames.CODE, "code-1");
        request.setParameter("code_verifier", "verifier");

        assertNull(converter.convert(request));
    }

    /**
     * 携带 client_secret 的请求交给 confidential 转换器。
     */
    @Test
    void convert_withClientSecret_returnsNull() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/oauth2/device_authorization");
        request.setParameter(OAuth2ParameterNames.CLIENT_ID, "conf-app");
        request.setParameter(OAuth2ParameterNames.CLIENT_SECRET, "secret");

        assertNull(converter.convert(request));
    }

    /**
     * Basic 认证请求交给 confidential 转换器。
     */
    @Test
    void convert_withBasicAuthorization_returnsNull() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/oauth2/device_authorization");
        request.addHeader("Authorization", "Basic Y2xpZW50OnNlY3JldA==");
        request.setParameter(OAuth2ParameterNames.CLIENT_ID, "conf-app");

        assertNull(converter.convert(request));
    }

    /**
     * 设备流场景缺少 client_id 时抛出 invalid_request。
     */
    @Test
    void convert_missingClientId_throwsInvalidRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/oauth2/device_authorization");
        request.setParameter(OAuth2ParameterNames.SCOPE, "profile");

        OAuth2AuthenticationException ex = assertThrows(
                OAuth2AuthenticationException.class,
                () -> converter.convert(request)
        );
        assertEquals(OAuth2ErrorCodes.INVALID_REQUEST, ex.getError().getErrorCode());
    }
}
