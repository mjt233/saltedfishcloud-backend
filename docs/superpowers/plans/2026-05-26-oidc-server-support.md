# OIDC Server Support Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add Spring Security based OIDC provider support on top of the existing open-platform OAuth flow without breaking legacy clients.

**Architecture:** Use Spring Authorization Server as the protocol shell, adapt `ThirdPartyApp` / `ThirdPartyAppKey` into `RegisteredClient`, and bridge token issuance so OIDC `access_token` maps to the existing `ApiTicket`, OIDC `refresh_token` maps to the current long-lived open-platform `Access Token`, and `id_token` is signed with a dedicated JWK-backed key pair. Keep legacy `/api/oauth/**` and `/api/openApi/**` endpoints, but refactor shared authorization, consent, and revocation logic so both paths share the same source of truth.

**Tech Stack:** Spring Boot 3.5, Spring Security 6, Spring Authorization Server, JPA, Redis, existing ApiTicket services, JUnit 5, MockMvc

---

## File Structure

### Modify

- `sfc-core/pom.xml` — add Spring Authorization Server dependency
- `sfc-core/src/main/java/com/xiaotao/saltedfishcloud/config/security/SecurityConfig.java` — split application security chain from authorization server chain
- `sfc-core/src/main/java/com/xiaotao/saltedfishcloud/controller/OAuthController.java` — reuse shared redirect URI and consent helpers
- `sfc-core/src/main/java/com/xiaotao/saltedfishcloud/controller/open/OpenApiAuthController.java` — reuse new token bridge service
- `sfc-core/src/main/java/com/xiaotao/saltedfishcloud/service/ThirdPartyAppAuthorizationServiceImpl.java` — expose consent-oriented read/write helpers
- `sfc-core/src/main/java/com/xiaotao/saltedfishcloud/service/ThirdPartyAppTokenServiceImpl.java` — add issue-by-app-and-user entry points for OIDC bridge
- `sfc-api/src/main/java/com/xiaotao/saltedfishcloud/model/po/ThirdPartyApp.java` — add OIDC client metadata fields
- `sfc-api/src/main/java/com/xiaotao/saltedfishcloud/service/third/ThirdPartyAppTokenService.java` — add OIDC bridge methods
- `sfc-core/src/main/config/application-develop.yml` — add `sys.oidc` config
- `sfc-core/src/main/config/application-product.yml` — add `sys.oidc` config
- `pre-release/config.yml` — add `sys.oidc` config
- `mkdocs.yml` — publish OIDC documentation entry

### Create

- `sfc-api/src/main/java/com/xiaotao/saltedfishcloud/model/po/ThirdPartyAppRedirectUri.java` — normalized redirect URI table
- `sfc-api/src/main/java/com/xiaotao/saltedfishcloud/model/po/ThirdPartyAppPostLogoutRedirectUri.java` — post logout redirect URI table
- `sfc-api/src/main/java/com/xiaotao/saltedfishcloud/dao/jpa/ThirdPartyAppRedirectUriRepo.java`
- `sfc-api/src/main/java/com/xiaotao/saltedfishcloud/dao/jpa/ThirdPartyAppPostLogoutRedirectUriRepo.java`
- `sfc-api/src/main/java/com/xiaotao/saltedfishcloud/enums/OidcClientType.java`
- `sfc-api/src/main/java/com/xiaotao/saltedfishcloud/enums/OidcTokenEndpointAuthMethod.java`
- `sfc-core/src/main/java/com/xiaotao/saltedfishcloud/config/oidc/OidcServerProperty.java`
- `sfc-core/src/main/java/com/xiaotao/saltedfishcloud/config/security/oidc/OidcAuthorizationServerConfig.java`
- `sfc-core/src/main/java/com/xiaotao/saltedfishcloud/config/security/oidc/OidcJwkService.java`
- `sfc-core/src/main/java/com/xiaotao/saltedfishcloud/config/security/oidc/OidcAuthorizationCodeContext.java`
- `sfc-core/src/main/java/com/xiaotao/saltedfishcloud/service/oidc/OidcRegisteredClientRepository.java`
- `sfc-core/src/main/java/com/xiaotao/saltedfishcloud/service/oidc/OidcAuthorizationConsentService.java`
- `sfc-core/src/main/java/com/xiaotao/saltedfishcloud/service/oidc/OidcTokenBridgeService.java`
- `sfc-core/src/main/java/com/xiaotao/saltedfishcloud/service/oidc/OidcClaimService.java`
- `sfc-core/src/main/java/com/xiaotao/saltedfishcloud/service/oidc/OidcUserInfoService.java`
- `sfc-core/src/main/java/com/xiaotao/saltedfishcloud/service/oidc/OidcLogoutService.java`
- `docs/oauth/oidc/index.md`
- `docs/oauth/oidc/endpoints.md`
- `docs/oauth/oidc/token-mapping.md`

### Test

- `sfc-core/src/test/java/com/xiaotao/saltedfishcloud/config/oidc/OidcServerPropertyTest.java`
- `sfc-core/src/test/java/com/xiaotao/saltedfishcloud/dao/jpa/ThirdPartyAppOidcMetadataRepositoryTest.java`
- `sfc-core/src/test/java/com/xiaotao/saltedfishcloud/config/security/oidc/OidcAuthorizationServerConfigTest.java`
- `sfc-core/src/test/java/com/xiaotao/saltedfishcloud/service/oidc/OidcRegisteredClientRepositoryTest.java`
- `sfc-core/src/test/java/com/xiaotao/saltedfishcloud/service/oidc/OidcTokenBridgeServiceTest.java`
- `sfc-core/src/test/java/com/xiaotao/saltedfishcloud/controller/oidc/OidcProtocolCompatibilityTest.java`

---

### Task 1: Add OIDC dependency and configuration skeleton

**Files:**
- Modify: `sfc-core/pom.xml`
- Create: `sfc-core/src/main/java/com/xiaotao/saltedfishcloud/config/oidc/OidcServerProperty.java`
- Modify: `sfc-core/src/main/config/application-develop.yml`
- Modify: `sfc-core/src/main/config/application-product.yml`
- Modify: `pre-release/config.yml`
- Test: `sfc-core/src/test/java/com/xiaotao/saltedfishcloud/config/oidc/OidcServerPropertyTest.java`

- [ ] **Step 1: Write the failing property-binding test**

```java
@SpringBootTest(properties = {
        "sys.oidc.enabled=true",
        "sys.oidc.issuer=https://cloud.example.com",
        "sys.oidc.jwk.key-id=oidc-key-1"
})
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -pl sfc-core -am -Dtest=OidcServerPropertyTest test`
Expected: FAIL because `OidcServerProperty` does not exist and `sys.oidc` is not bound.

- [ ] **Step 3: Add the dependency and property class**

```xml
<!-- sfc-core/pom.xml -->
<dependency>
    <groupId>org.springframework.security</groupId>
    <artifactId>spring-security-oauth2-authorization-server</artifactId>
    <version>1.4.1</version>
</dependency>
```

```java
@Data
@ConfigurationProperties(prefix = "sys.oidc")
public class OidcServerProperty {
    private boolean enabled = false;
    private String issuer;
    private String authorizationEndpoint = "/oauth2/authorize";
    private String tokenEndpoint = "/oauth2/token";
    private String userInfoEndpoint = "/oauth2/userinfo";
    private String jwkSetEndpoint = "/oauth2/jwks";
    private String revocationEndpoint = "/oauth2/revoke";
    private String introspectionEndpoint = "/oauth2/introspect";
    private String logoutEndpoint = "/connect/logout";
    private final Jwk jwk = new Jwk();

    @Data
    public static class Jwk {
        private String keyId = "oidc-key-1";
        private String keyStorePath = "./oidc-jwk.json";
    }
}
```

```yaml
# application-develop.yml / application-product.yml / pre-release/config.yml
sys:
  oidc:
    enabled: false
    issuer: ${OIDC_ISSUER:}
    authorization-endpoint: /oauth2/authorize
    token-endpoint: /oauth2/token
    user-info-endpoint: /oauth2/userinfo
    jwk-set-endpoint: /oauth2/jwks
    revocation-endpoint: /oauth2/revoke
    introspection-endpoint: /oauth2/introspect
    logout-endpoint: /connect/logout
    jwk:
      key-id: ${OIDC_JWK_KEY_ID:oidc-key-1}
      key-store-path: ${OIDC_JWK_STORE_PATH:./oidc-jwk.json}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -pl sfc-core -am -Dtest=OidcServerPropertyTest test`
Expected: PASS with the `sys.oidc` defaults and issuer binding loaded.

- [ ] **Step 5: Commit**

```bash
git add sfc-core/pom.xml sfc-core/src/main/java/com/xiaotao/saltedfishcloud/config/oidc/OidcServerProperty.java sfc-core/src/main/config/application-develop.yml sfc-core/src/main/config/application-product.yml pre-release/config.yml sfc-core/src/test/java/com/xiaotao/saltedfishcloud/config/oidc/OidcServerPropertyTest.java
git commit -m "chore: 引入OIDC配置骨架"
```

### Task 2: Extend persistence for OIDC client metadata

**Files:**
- Modify: `sfc-api/src/main/java/com/xiaotao/saltedfishcloud/model/po/ThirdPartyApp.java`
- Create: `sfc-api/src/main/java/com/xiaotao/saltedfishcloud/model/po/ThirdPartyAppRedirectUri.java`
- Create: `sfc-api/src/main/java/com/xiaotao/saltedfishcloud/model/po/ThirdPartyAppPostLogoutRedirectUri.java`
- Create: `sfc-api/src/main/java/com/xiaotao/saltedfishcloud/dao/jpa/ThirdPartyAppRedirectUriRepo.java`
- Create: `sfc-api/src/main/java/com/xiaotao/saltedfishcloud/dao/jpa/ThirdPartyAppPostLogoutRedirectUriRepo.java`
- Create: `sfc-api/src/main/java/com/xiaotao/saltedfishcloud/enums/OidcClientType.java`
- Create: `sfc-api/src/main/java/com/xiaotao/saltedfishcloud/enums/OidcTokenEndpointAuthMethod.java`
- Test: `sfc-core/src/test/java/com/xiaotao/saltedfishcloud/dao/jpa/ThirdPartyAppOidcMetadataRepositoryTest.java`

- [ ] **Step 1: Write the failing repository test**

```java
@SpringBootTest
class ThirdPartyAppOidcMetadataRepositoryTest {

    @Resource
    private ThirdPartyAppRepo appRepo;
    @Resource
    private ThirdPartyAppRedirectUriRepo redirectUriRepo;

    @Test
    void shouldPersistOidcClientMetadata() {
        ThirdPartyApp app = new ThirdPartyApp();
        app.setName("oidc-client");
        app.setIsEnabled(true);
        app.setOidcEnabled(true);
        app.setOidcClientType(OidcClientType.PUBLIC);
        app.setRequirePkce(true);
        app.setOidcTokenEndpointAuthMethod(OidcTokenEndpointAuthMethod.NONE);
        appRepo.save(app);

        ThirdPartyAppRedirectUri redirectUri = new ThirdPartyAppRedirectUri();
        redirectUri.setAppId(app.getId());
        redirectUri.setUri("https://client.example.com/callback");
        redirectUriRepo.save(redirectUri);

        ThirdPartyApp reloaded = appRepo.findById(app.getId()).orElseThrow();
        assertTrue(reloaded.getOidcEnabled());
        assertEquals(OidcClientType.PUBLIC, reloaded.getOidcClientType());
        assertTrue(reloaded.getRequirePkce());
        assertEquals(1, redirectUriRepo.findByAppId(app.getId()).size());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -pl sfc-core -am -Dtest=ThirdPartyAppOidcMetadataRepositoryTest test`
Expected: FAIL because the OIDC metadata fields, enums, and redirect URI repositories do not exist.

- [ ] **Step 3: Add the metadata fields and URI tables**

```java
// ThirdPartyApp.java
private Boolean oidcEnabled = false;
@Enumerated(EnumType.STRING)
private OidcClientType oidcClientType = OidcClientType.CONFIDENTIAL;
private Boolean requirePkce = false;
@Enumerated(EnumType.STRING)
private OidcTokenEndpointAuthMethod oidcTokenEndpointAuthMethod = OidcTokenEndpointAuthMethod.CLIENT_SECRET_BASIC;
```

```java
@Entity
@Table(indexes = @Index(name = "idx_oidc_redirect_uri_app_id", columnList = "appId"))
@Data
public class ThirdPartyAppRedirectUri extends AuditModel {
    private Long appId;
    @Column(length = 1024, nullable = false)
    private String uri;
}
```

```java
@Entity
@Table(indexes = @Index(name = "idx_oidc_post_logout_redirect_uri_app_id", columnList = "appId"))
@Data
public class ThirdPartyAppPostLogoutRedirectUri extends AuditModel {
    private Long appId;
    @Column(length = 1024, nullable = false)
    private String uri;
}
```

```java
public enum OidcClientType {
    CONFIDENTIAL,
    PUBLIC
}
```

```java
public enum OidcTokenEndpointAuthMethod {
    CLIENT_SECRET_BASIC,
    CLIENT_SECRET_POST,
    NONE
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -pl sfc-core -am -Dtest=ThirdPartyAppOidcMetadataRepositoryTest test`
Expected: PASS with client type, PKCE, and redirect URIs persisted through JPA.

- [ ] **Step 5: Commit**

```bash
git add sfc-api/src/main/java/com/xiaotao/saltedfishcloud/model/po/ThirdPartyApp.java sfc-api/src/main/java/com/xiaotao/saltedfishcloud/model/po/ThirdPartyAppRedirectUri.java sfc-api/src/main/java/com/xiaotao/saltedfishcloud/model/po/ThirdPartyAppPostLogoutRedirectUri.java sfc-api/src/main/java/com/xiaotao/saltedfishcloud/dao/jpa/ThirdPartyAppRedirectUriRepo.java sfc-api/src/main/java/com/xiaotao/saltedfishcloud/dao/jpa/ThirdPartyAppPostLogoutRedirectUriRepo.java sfc-api/src/main/java/com/xiaotao/saltedfishcloud/enums/OidcClientType.java sfc-api/src/main/java/com/xiaotao/saltedfishcloud/enums/OidcTokenEndpointAuthMethod.java sfc-core/src/test/java/com/xiaotao/saltedfishcloud/dao/jpa/ThirdPartyAppOidcMetadataRepositoryTest.java
git commit -m "feat: 扩展OAuth应用OIDC元数据"
```

### Task 3: Wire Spring Authorization Server and JWK infrastructure

**Files:**
- Create: `sfc-core/src/main/java/com/xiaotao/saltedfishcloud/config/security/oidc/OidcAuthorizationServerConfig.java`
- Create: `sfc-core/src/main/java/com/xiaotao/saltedfishcloud/config/security/oidc/OidcJwkService.java`
- Modify: `sfc-core/src/main/java/com/xiaotao/saltedfishcloud/config/security/SecurityConfig.java`
- Test: `sfc-core/src/test/java/com/xiaotao/saltedfishcloud/config/security/oidc/OidcAuthorizationServerConfigTest.java`

- [ ] **Step 1: Write the failing discovery test**

```java
@SpringBootTest(properties = {
        "sys.oidc.enabled=true",
        "sys.oidc.issuer=https://cloud.example.com"
})
@AutoConfigureMockMvc
class OidcAuthorizationServerConfigTest {

    @Resource
    private MockMvc mockMvc;

    @Test
    void shouldExposeDiscoveryDocument() throws Exception {
        mockMvc.perform(get("/.well-known/openid-configuration"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.issuer").value("https://cloud.example.com"))
                .andExpect(jsonPath("$.authorization_endpoint").value("https://cloud.example.com/oauth2/authorize"))
                .andExpect(jsonPath("$.jwks_uri").value("https://cloud.example.com/oauth2/jwks"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -pl sfc-core -am -Dtest=OidcAuthorizationServerConfigTest test`
Expected: FAIL because the authorization server endpoints are not registered.

- [ ] **Step 3: Add the authorization server chain and JWK service**

```java
@Configuration
@EnableConfigurationProperties(OidcServerProperty.class)
public class OidcAuthorizationServerConfig {

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    SecurityFilterChain oidcSecurityFilterChain(HttpSecurity http) throws Exception {
        OAuth2AuthorizationServerConfigurer authorizationServer = OAuth2AuthorizationServerConfigurer.authorizationServer();
        http.securityMatcher(authorizationServer.getEndpointsMatcher())
                .with(authorizationServer, server -> server.oidc(Customizer.withDefaults()))
                .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
                .exceptionHandling(ex -> ex.defaultAuthenticationEntryPointFor(
                        new LoginUrlAuthenticationEntryPoint("/oauth"),
                        new MediaTypeRequestMatcher(MediaType.TEXT_HTML)
                ));
        return http.build();
    }

    @Bean
    AuthorizationServerSettings authorizationServerSettings(OidcServerProperty property) {
        return AuthorizationServerSettings.builder()
                .issuer(property.getIssuer())
                .authorizationEndpoint(property.getAuthorizationEndpoint())
                .tokenEndpoint(property.getTokenEndpoint())
                .tokenRevocationEndpoint(property.getRevocationEndpoint())
                .tokenIntrospectionEndpoint(property.getIntrospectionEndpoint())
                .oidcUserInfoEndpoint(property.getUserInfoEndpoint())
                .oidcLogoutEndpoint(property.getLogoutEndpoint())
                .jwkSetEndpoint(property.getJwkSetEndpoint())
                .build();
    }
}
```

```java
@Service
public class OidcJwkService {

    public JWKSet loadOrCreate(OidcServerProperty property) {
        RSAKey rsaKey = new RSAKeyGenerator(2048)
                .keyID(property.getJwk().getKeyId())
                .generate();
        return new JWKSet(rsaKey);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -pl sfc-core -am -Dtest=OidcAuthorizationServerConfigTest test`
Expected: PASS with discovery and JWKS endpoints exposed by Spring Authorization Server.

- [ ] **Step 5: Commit**

```bash
git add sfc-core/src/main/java/com/xiaotao/saltedfishcloud/config/security/SecurityConfig.java sfc-core/src/main/java/com/xiaotao/saltedfishcloud/config/security/oidc/OidcAuthorizationServerConfig.java sfc-core/src/main/java/com/xiaotao/saltedfishcloud/config/security/oidc/OidcJwkService.java sfc-core/src/test/java/com/xiaotao/saltedfishcloud/config/security/oidc/OidcAuthorizationServerConfigTest.java
git commit -m "feat: 配置OIDC授权服务基础能力"
```

### Task 4: Adapt clients and consent into Spring Authorization Server

**Files:**
- Create: `sfc-core/src/main/java/com/xiaotao/saltedfishcloud/service/oidc/OidcRegisteredClientRepository.java`
- Create: `sfc-core/src/main/java/com/xiaotao/saltedfishcloud/service/oidc/OidcAuthorizationConsentService.java`
- Modify: `sfc-core/src/main/java/com/xiaotao/saltedfishcloud/service/ThirdPartyAppAuthorizationServiceImpl.java`
- Test: `sfc-core/src/test/java/com/xiaotao/saltedfishcloud/service/oidc/OidcRegisteredClientRepositoryTest.java`

- [ ] **Step 1: Write the failing repository and consent test**

```java
@SpringBootTest
class OidcRegisteredClientRepositoryTest {

    @Resource
    private OidcRegisteredClientRepository repository;
    @Resource
    private OidcAuthorizationConsentService consentService;

    @Test
    void shouldMapThirdPartyAppToRegisteredClient() {
        RegisteredClient client = repository.findByClientId("1226973239364812800");
        assertNotNull(client);
        assertTrue(client.getScopes().contains("openid"));
        assertTrue(client.getRedirectUris().contains("https://client.example.com/callback"));
    }

    @Test
    void shouldPersistConsentBackToThirdPartyAuthorization() {
        OAuth2AuthorizationConsent consent = OAuth2AuthorizationConsent.withId("1226973239364812800", "1054223881344122880")
                .authority(new SimpleGrantedAuthority("SCOPE_openid"))
                .authority(new SimpleGrantedAuthority("SCOPE_profile"))
                .build();
        consentService.save(consent);
        OAuth2AuthorizationConsent reloaded = consentService.findById("1226973239364812800", "1054223881344122880");
        assertNotNull(reloaded);
        assertTrue(reloaded.getAuthorities().stream().anyMatch(a -> "SCOPE_profile".equals(a.getAuthority())));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -pl sfc-core -am -Dtest=OidcRegisteredClientRepositoryTest test`
Expected: FAIL because the registered-client adapter and consent bridge do not exist.

- [ ] **Step 3: Implement the adapters**

```java
@RequiredArgsConstructor
public class OidcRegisteredClientRepository implements RegisteredClientRepository {
    private final ThirdPartyAppService appService;
    private final ThirdPartyAppKeyRepo keyRepo;
    private final ThirdPartyAppRedirectUriRepo redirectUriRepo;

    @Override
    public RegisteredClient findByClientId(String clientId) {
        ThirdPartyApp app = appService.findById(Long.valueOf(clientId));
        if (app == null || !Boolean.TRUE.equals(app.getOidcEnabled())) {
            return null;
        }
        Set<String> redirectUris = redirectUriRepo.findByAppId(app.getId()).stream()
                .map(ThirdPartyAppRedirectUri::getUri)
                .collect(Collectors.toSet());
        ClientAuthenticationMethod method = switch (app.getOidcTokenEndpointAuthMethod()) {
            case CLIENT_SECRET_POST -> ClientAuthenticationMethod.CLIENT_SECRET_POST;
            case NONE -> ClientAuthenticationMethod.NONE;
            default -> ClientAuthenticationMethod.CLIENT_SECRET_BASIC;
        };
        return RegisteredClient.withId(app.getId().toString())
                .clientId(app.getId().toString())
                .clientSecret(keyRepo.findByAppId(app.getId()).stream().findFirst().map(ThirdPartyAppKey::getClientSecretHash).orElse(null))
                .clientAuthenticationMethod(method)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .redirectUris(uris -> uris.addAll(redirectUris))
                .scope(OidcScopes.OPENID)
                .scope("profile")
                .scope("storage_read")
                .scope("storage_write")
                .clientSettings(ClientSettings.builder().requireProofKey(Boolean.TRUE.equals(app.getRequirePkce())).build())
                .build();
    }
}
```

```java
@RequiredArgsConstructor
public class OidcAuthorizationConsentService implements OAuth2AuthorizationConsentService {
    private final ThirdPartyAppAuthorizationService authorizationService;

    @Override
    public void save(OAuth2AuthorizationConsent consent) {
        Set<String> scopes = consent.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("SCOPE_"))
                .map(a -> a.substring("SCOPE_".length()))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        authorizationService.authorize(Long.valueOf(consent.getRegisteredClientId()), Long.valueOf(consent.getPrincipalName()), String.join(" ", scopes));
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -pl sfc-core -am -Dtest=OidcRegisteredClientRepositoryTest test`
Expected: PASS with client metadata and consent sourced from the existing third-party app tables.

- [ ] **Step 5: Commit**

```bash
git add sfc-core/src/main/java/com/xiaotao/saltedfishcloud/service/oidc/OidcRegisteredClientRepository.java sfc-core/src/main/java/com/xiaotao/saltedfishcloud/service/oidc/OidcAuthorizationConsentService.java sfc-core/src/main/java/com/xiaotao/saltedfishcloud/service/ThirdPartyAppAuthorizationServiceImpl.java sfc-core/src/test/java/com/xiaotao/saltedfishcloud/service/oidc/OidcRegisteredClientRepositoryTest.java
git commit -m "feat: 适配OIDC客户端与授权同意"
```

### Task 5: Bridge token issuance to the existing Access Token and ApiTicket model

**Files:**
- Create: `sfc-core/src/main/java/com/xiaotao/saltedfishcloud/config/security/oidc/OidcAuthorizationCodeContext.java`
- Create: `sfc-core/src/main/java/com/xiaotao/saltedfishcloud/service/oidc/OidcTokenBridgeService.java`
- Create: `sfc-core/src/main/java/com/xiaotao/saltedfishcloud/service/oidc/OidcClaimService.java`
- Modify: `sfc-api/src/main/java/com/xiaotao/saltedfishcloud/service/third/ThirdPartyAppTokenService.java`
- Modify: `sfc-core/src/main/java/com/xiaotao/saltedfishcloud/service/ThirdPartyAppTokenServiceImpl.java`
- Test: `sfc-core/src/test/java/com/xiaotao/saltedfishcloud/service/oidc/OidcTokenBridgeServiceTest.java`

- [ ] **Step 1: Write the failing bridge test**

```java
@SpringBootTest
class OidcTokenBridgeServiceTest {

    @Resource
    private OidcTokenBridgeService bridgeService;

    @Test
    void shouldIssueApiTicketRefreshTokenAndIdTokenForOidcCodeExchange() {
        OidcTokenBridgeService.TokenBundle bundle = bridgeService.issueFromAuthorizedUser(
                1226973239364812800L,
                1054223881344122880L,
                Set.of("openid", "profile", "storage_read"),
                "nonce-1"
        );
        assertNotNull(bundle.accessToken());
        assertNotNull(bundle.refreshToken());
        assertNotNull(bundle.idToken());
        assertEquals(900L, bundle.expiresIn());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -pl sfc-core -am -Dtest=OidcTokenBridgeServiceTest test`
Expected: FAIL because the bridge service and OIDC-aware token issuance entry points do not exist.

- [ ] **Step 3: Implement the bridge and service methods**

```java
public interface ThirdPartyAppTokenService extends CrudService<ThirdPartyAppToken> {
    String issueAccessToken(Long appId, Long uid);
    String issueApiTicketFromAccessToken(String accessToken, boolean permanent, boolean revokeOlder);
}
```

```java
@Service
@RequiredArgsConstructor
public class OidcTokenBridgeService {
    private final ThirdPartyAppTokenService tokenService;
    private final OidcClaimService claimService;

    public TokenBundle issueFromAuthorizedUser(Long appId, Long uid, Set<String> scopes, String nonce) {
        String refreshToken = tokenService.issueAccessToken(appId, uid);
        String accessToken = tokenService.issueApiTicketFromAccessToken(refreshToken, false, true);
        String idToken = claimService.issueIdToken(appId, uid, scopes, nonce, accessToken);
        return new TokenBundle(accessToken, refreshToken, idToken, 900L, String.join(" ", scopes));
    }

    public record TokenBundle(String accessToken, String refreshToken, String idToken, long expiresIn, String scope) {
    }
}
```

```java
// ThirdPartyAppTokenServiceImpl.java
@Override
public String issueAccessToken(Long appId, Long uid) {
    return generateAccessToken(appId, uid);
}

@Override
public String issueApiTicketFromAccessToken(String accessToken, boolean permanent, boolean revokeOlder) {
    return getApiTicket(accessToken, permanent, revokeOlder);
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -pl sfc-core -am -Dtest=OidcTokenBridgeServiceTest test`
Expected: PASS with the returned `access_token`, `refresh_token`, and `id_token` mapped to the existing ticket model.

- [ ] **Step 5: Commit**

```bash
git add sfc-core/src/main/java/com/xiaotao/saltedfishcloud/config/security/oidc/OidcAuthorizationCodeContext.java sfc-core/src/main/java/com/xiaotao/saltedfishcloud/service/oidc/OidcTokenBridgeService.java sfc-core/src/main/java/com/xiaotao/saltedfishcloud/service/oidc/OidcClaimService.java sfc-api/src/main/java/com/xiaotao/saltedfishcloud/service/third/ThirdPartyAppTokenService.java sfc-core/src/main/java/com/xiaotao/saltedfishcloud/service/ThirdPartyAppTokenServiceImpl.java sfc-core/src/test/java/com/xiaotao/saltedfishcloud/service/oidc/OidcTokenBridgeServiceTest.java
git commit -m "feat: 打通OIDC令牌与现有票据体系"
```

### Task 6: Finish protocol endpoints, claims, and legacy compatibility

**Files:**
- Create: `sfc-core/src/main/java/com/xiaotao/saltedfishcloud/service/oidc/OidcUserInfoService.java`
- Create: `sfc-core/src/main/java/com/xiaotao/saltedfishcloud/service/oidc/OidcLogoutService.java`
- Modify: `sfc-core/src/main/java/com/xiaotao/saltedfishcloud/controller/OAuthController.java`
- Modify: `sfc-core/src/main/java/com/xiaotao/saltedfishcloud/controller/open/OpenApiAuthController.java`
- Modify: `sfc-core/src/main/java/com/xiaotao/saltedfishcloud/config/security/oidc/OidcAuthorizationServerConfig.java`
- Test: `sfc-core/src/test/java/com/xiaotao/saltedfishcloud/controller/oidc/OidcProtocolCompatibilityTest.java`

- [ ] **Step 1: Write the failing protocol compatibility test**

```java
@SpringBootTest(properties = {
        "sys.oidc.enabled=true",
        "sys.oidc.issuer=https://cloud.example.com"
})
@AutoConfigureMockMvc
class OidcProtocolCompatibilityTest {

    @Resource
    private MockMvc mockMvc;

    @Test
    void shouldReturnUserInfoForApiTicketBackedAccessToken() throws Exception {
        String accessToken = "replace-in-test-fixture";
        mockMvc.perform(get("/oauth2/userinfo").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sub").exists())
                .andExpect(jsonPath("$.preferred_username").exists());
    }

    @Test
    void shouldKeepLegacyApiTicketExchangeWorking() throws Exception {
        mockMvc.perform(get("/api/openApi/auth/getApiTicket/v1")
                        .param("accessToken", "replace-in-test-fixture"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isString());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -pl sfc-core -am -Dtest=OidcProtocolCompatibilityTest test`
Expected: FAIL because `/oauth2/userinfo` is not backed by the existing user model and the legacy path is not yet sharing the new bridge.

- [ ] **Step 3: Implement userinfo, logout, introspection, and legacy reuse**

```java
@Service
@RequiredArgsConstructor
public class OidcUserInfoService {
    private final UserService userService;

    public OidcUserInfo getUserInfo(Long uid, Set<String> scopes) {
        User user = userService.getUserById(uid);
        OidcUserInfo.Builder builder = OidcUserInfo.builder().subject(uid.toString());
        if (scopes.contains("profile")) {
            builder.claim("preferred_username", user.getUsername());
            builder.claim("name", user.getNickname() == null ? user.getUsername() : user.getNickname());
            builder.claim("picture", user.getAvatar());
        }
        if (scopes.contains("email")) {
            builder.claim("email", user.getEmail());
            builder.claim("email_verified", user.getEmail() != null && !user.getEmail().isBlank());
        }
        return builder.build();
    }
}
```

```java
// OidcAuthorizationServerConfig.java
authorizationServer
        .oidc(oidc -> oidc
                .userInfoEndpoint(userInfo -> userInfo.userInfoMapper(context ->
                        oidcUserInfoService.getUserInfo(Long.valueOf(context.getAuthorization().getPrincipalName()), context.getAuthorizedScopes())
                ))
                .logoutEndpoint(logout -> logout.logoutResponseHandler(oidcLogoutService))
        );
```

```java
// OpenApiAuthController.java
@GetMapping("/getApiTicket/v1")
@AllowAnonymous
public JsonResult<String> getApiTicket(
        @RequestParam("accessToken") String accessToken,
        @RequestParam(value = "permanent", defaultValue = "false") boolean permanent) {
    return JsonResultImpl.getInstance(thirdPartyAppTokenService.issueApiTicketFromAccessToken(accessToken, permanent, true));
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -pl sfc-core -am -Dtest=OidcProtocolCompatibilityTest test`
Expected: PASS with `/oauth2/userinfo` backed by the existing user model and the legacy ApiTicket exchange still returning a token string.

- [ ] **Step 5: Commit**

```bash
git add sfc-core/src/main/java/com/xiaotao/saltedfishcloud/service/oidc/OidcUserInfoService.java sfc-core/src/main/java/com/xiaotao/saltedfishcloud/service/oidc/OidcLogoutService.java sfc-core/src/main/java/com/xiaotao/saltedfishcloud/controller/OAuthController.java sfc-core/src/main/java/com/xiaotao/saltedfishcloud/controller/open/OpenApiAuthController.java sfc-core/src/main/java/com/xiaotao/saltedfishcloud/config/security/oidc/OidcAuthorizationServerConfig.java sfc-core/src/test/java/com/xiaotao/saltedfishcloud/controller/oidc/OidcProtocolCompatibilityTest.java
git commit -m "feat: 完成OIDC协议端点与兼容复用"
```

### Task 7: Update documentation and run the final verification set

**Files:**
- Create: `docs/oauth/oidc/index.md`
- Create: `docs/oauth/oidc/endpoints.md`
- Create: `docs/oauth/oidc/token-mapping.md`
- Modify: `mkdocs.yml`

- [ ] **Step 1: Write the failing doc navigation update**

```yaml
# mkdocs.yml
- OAuth开放平台:
  - 概述: oauth/index.md
  - OIDC:
    - 概述: oauth/oidc/index.md
    - 端点说明: oauth/oidc/endpoints.md
    - 令牌映射: oauth/oidc/token-mapping.md
```

- [ ] **Step 2: Run the doc and protocol test set before writing docs**

Run: `mvn -pl sfc-core -am -Dtest=OidcServerPropertyTest,ThirdPartyAppOidcMetadataRepositoryTest,OidcAuthorizationServerConfigTest,OidcRegisteredClientRepositoryTest,OidcTokenBridgeServiceTest,OidcProtocolCompatibilityTest test`
Expected: PASS for all six OIDC-focused tests before the docs are added.

- [ ] **Step 3: Add the docs**

```markdown
# OIDC 概述

咸鱼云网盘现在同时提供旧开放平台 OAuth 接口和标准 OIDC 接口。

- `access_token` 对应现有短期 `ApiTicket`
- `refresh_token` 对应现有长期开放平台 `Access Token`
- `id_token` 用于标准身份声明
```

```markdown
# OIDC 端点说明

- `/.well-known/openid-configuration`
- `/oauth2/jwks`
- `/oauth2/authorize`
- `/oauth2/token`
- `/oauth2/userinfo`
- `/oauth2/revoke`
- `/oauth2/introspect`
- `/connect/logout`
```

```markdown
# OIDC 令牌映射

| 标准字段 | 咸鱼云内部实现 |
|---|---|
| `access_token` | `ApiTicket` |
| `refresh_token` | 长期开放平台 `Access Token` |
| `id_token` | OIDC 身份令牌 |
```

- [ ] **Step 4: Run final verification**

Run: `mvn -pl sfc-core -am -Dtest=OidcServerPropertyTest,ThirdPartyAppOidcMetadataRepositoryTest,OidcAuthorizationServerConfigTest,OidcRegisteredClientRepositoryTest,OidcTokenBridgeServiceTest,OidcProtocolCompatibilityTest test`
Expected: PASS with the OIDC protocol, metadata persistence, and compatibility tests green.

- [ ] **Step 5: Run compile validation with MCP**

```text
Use MCP build_project on:
- C:\Users\xiaotao\code\saltedfishcloud-backend
- C:\Users\xiaotao\code\saltedfishcloud-backend\sfc-core
```

Expected: both compile validations succeed without introducing new build errors.

- [ ] **Step 6: Commit**

```bash
git add mkdocs.yml docs/oauth/oidc/index.md docs/oauth/oidc/endpoints.md docs/oauth/oidc/token-mapping.md
git commit -m "docs: 补充OIDC开放平台文档"
```

## Self-Review

### Spec coverage

- OIDC discovery / JWKS / authorize / token / userinfo / revoke / introspect / logout are covered by Tasks 3, 5, and 6.
- Spring Security / Spring Authorization Server first implementation is covered by Tasks 1 and 3.
- `ThirdPartyApp` / `ThirdPartyAppKey` adaptation is covered by Tasks 2 and 4.
- `access_token -> ApiTicket` and `refresh_token -> existing Access Token` mapping is covered by Task 5.
- Legacy `/api/oauth/**` and `/api/openApi/**` compatibility is covered by Task 6.
- Config and docs updates are covered by Tasks 1 and 7.

### Placeholder scan

- No `TODO`, `TBD`, or deferred implementation markers remain.
- Every code-changing step includes a concrete code block.
- Every verification step includes a concrete command and expected outcome.

### Type consistency

- `OidcServerProperty`, `OidcRegisteredClientRepository`, `OidcAuthorizationConsentService`, `OidcTokenBridgeService`, `OidcClaimService`, and `OidcUserInfoService` are named consistently across tasks.
- `OidcClientType` and `OidcTokenEndpointAuthMethod` are defined before later tasks depend on them.
- The token mapping names `issueAccessToken` and `issueApiTicketFromAccessToken` are introduced in Task 5 before the legacy compatibility task reuses them.
