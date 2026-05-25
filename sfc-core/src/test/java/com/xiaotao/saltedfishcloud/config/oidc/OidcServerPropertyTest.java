package com.xiaotao.saltedfishcloud.config.oidc;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import jakarta.annotation.Resource;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(
        classes = OidcConfig.class,
        properties = {
                "sys.oidc.enabled=true",
                "sys.oidc.issuer=https://cloud.example.com",
                "sys.oidc.jwk.key-id=oidc-key-1"
        }
)
class OidcServerPropertyTest {

    @Resource
    private OidcServerProperty property;

    @Test
    void shouldBindIssuerAndDefaultEndpoints() {
        assertTrue(property.isEnabled());
        assertEquals("https://cloud.example.com", property.getIssuer());
        assertEquals("/oauth2/authorize", property.getAuthorizationEndpoint());
        assertEquals("/oauth2/token", property.getTokenEndpoint());
        assertEquals("/oauth2/userinfo", property.getUserInfoEndpoint());
        assertEquals("oidc-key-1", property.getJwk().getKeyId());
    }
}

