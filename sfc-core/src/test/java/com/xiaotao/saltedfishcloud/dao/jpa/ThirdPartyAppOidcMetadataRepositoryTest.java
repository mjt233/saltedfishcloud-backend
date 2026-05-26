package com.xiaotao.saltedfishcloud.dao.jpa;

import com.xiaotao.saltedfishcloud.enums.OidcClientType;
import com.xiaotao.saltedfishcloud.enums.OidcTokenEndpointAuthMethod;
import com.xiaotao.saltedfishcloud.model.po.ThirdPartyApp;
import com.xiaotao.saltedfishcloud.model.po.ThirdPartyAppRedirectUri;
import com.xiaotao.saltedfishcloud.model.po.ThirdPartyAppPostLogoutRedirectUri;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * ThirdPartyApp OIDC 元数据字段与相关 Repository 的持久化集成测试。
 * <p>
 * 使用 {@link DataJpaTest} 和嵌入式 H2 数据库，验证 OIDC 字段的 JPA 读写往返
 * （save → flush → clear → findById），以及 {@link ThirdPartyAppRedirectUriRepo}
 * 的 {@code findByAppId} 和 {@code deleteByAppId} 在真实数据库上的行为。
 * 不依赖完整 Spring Boot 上下文。JPA 审计由测试引导类 {@link JpaTestApplication}
 * 上的 {@code @EnableJpaAuditing} 提供，无需在测试中重复声明。
 * </p>
 */
@DataJpaTest(properties = {
        "spring.jpa.database=default",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
class ThirdPartyAppOidcMetadataRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ThirdPartyAppRepo thirdPartyAppRepo;

    @Autowired
    private ThirdPartyAppRedirectUriRepo thirdPartyAppRedirectUriRepo;

    @Autowired
    private ThirdPartyAppPostLogoutRedirectUriRepo thirdPartyAppPostLogoutRedirectUriRepo;

    // -------------------------------------------------------------------------
    // ThirdPartyApp OIDC 字段持久化
    // -------------------------------------------------------------------------

    /**
     * 验证 {@link ThirdPartyApp} 的 OIDC 元数据字段能被正确持久化并重新读取。
     * 覆盖需求：save → flush → clear → findById 后断言字段值不变。
     */
    @Test
    void shouldPersistOidcClientMetadata() {
        // Given
        ThirdPartyApp app = new ThirdPartyApp();
        app.setName("oidc-client-test");
        app.setIsEnabled(true);
        app.setOidcEnabled(true);
        app.setOidcClientType(OidcClientType.PUBLIC);
        app.setRequirePkce(true);
        app.setOidcTokenEndpointAuthMethod(OidcTokenEndpointAuthMethod.NONE);

        // When
        ThirdPartyApp saved = thirdPartyAppRepo.save(app);
        entityManager.flush();
        entityManager.clear();

        // Then — reload from DB bypassing the first-level cache
        Optional<ThirdPartyApp> found = thirdPartyAppRepo.findById(saved.getId());
        assertTrue(found.isPresent(), "持久化后应能根据 ID 查询到实体");
        ThirdPartyApp reloaded = found.get();

        assertTrue(reloaded.getOidcEnabled(), "oidcEnabled 应为 true");
        assertEquals(OidcClientType.PUBLIC, reloaded.getOidcClientType(), "oidcClientType 应为 PUBLIC");
        assertTrue(reloaded.getRequirePkce(), "requirePkce 应为 true");
        assertEquals(OidcTokenEndpointAuthMethod.NONE, reloaded.getOidcTokenEndpointAuthMethod(),
                "oidcTokenEndpointAuthMethod 应为 NONE");
    }

    /**
     * 验证 {@link ThirdPartyApp} OIDC 字段的默认值在持久化读取后仍然正确。
     */
    @Test
    void shouldPersistDefaultOidcValues() {
        // Given — 仅设置必要字段，OIDC 字段使用默认值
        ThirdPartyApp app = new ThirdPartyApp();
        app.setName("default-oidc-test");

        // When
        ThirdPartyApp saved = thirdPartyAppRepo.save(app);
        entityManager.flush();
        entityManager.clear();

        // Then
        ThirdPartyApp reloaded = thirdPartyAppRepo.findById(saved.getId()).orElseThrow();

        assertFalse(reloaded.getOidcEnabled(), "oidcEnabled 默认应为 false");
        assertEquals(OidcClientType.CONFIDENTIAL, reloaded.getOidcClientType(),
                "oidcClientType 默认应为 CONFIDENTIAL");
        assertFalse(reloaded.getRequirePkce(), "requirePkce 默认应为 false");
        assertEquals(OidcTokenEndpointAuthMethod.CLIENT_SECRET_BASIC,
                reloaded.getOidcTokenEndpointAuthMethod(),
                "oidcTokenEndpointAuthMethod 默认应为 CLIENT_SECRET_BASIC");
    }

    // -------------------------------------------------------------------------
    // ThirdPartyAppRedirectUriRepo 持久化行为
    // -------------------------------------------------------------------------

    /**
     * 验证 {@link ThirdPartyAppRedirectUriRepo#findByAppId(Long)} 能返回已持久化的重定向 URI 列表。
     */
    @Test
    void shouldFindRedirectUrisByAppId() {
        // Given
        ThirdPartyApp app = new ThirdPartyApp();
        app.setName("find-uri-test");
        ThirdPartyApp saved = thirdPartyAppRepo.save(app);
        entityManager.flush();

        ThirdPartyAppRedirectUri uri1 = new ThirdPartyAppRedirectUri();
        uri1.setAppId(saved.getId());
        uri1.setUri("https://client.example.com/callback1");

        ThirdPartyAppRedirectUri uri2 = new ThirdPartyAppRedirectUri();
        uri2.setAppId(saved.getId());
        uri2.setUri("https://client.example.com/callback2");

        thirdPartyAppRedirectUriRepo.save(uri1);
        thirdPartyAppRedirectUriRepo.save(uri2);
        entityManager.flush();
        entityManager.clear();

        // When
        List<ThirdPartyAppRedirectUri> found = thirdPartyAppRedirectUriRepo.findByAppId(saved.getId());

        // Then
        assertThat(found).hasSize(2);
        assertThat(found)
                .extracting(ThirdPartyAppRedirectUri::getUri)
                .containsExactlyInAnyOrder(
                        "https://client.example.com/callback1",
                        "https://client.example.com/callback2"
                );
    }

    /**
     * 验证 {@link ThirdPartyAppRedirectUriRepo#deleteByAppId(Long)} 能删除指定应用的全部重定向 URI。
     */
    @Test
    void shouldDeleteRedirectUrisByAppId() {
        // Given
        ThirdPartyApp app = new ThirdPartyApp();
        app.setName("delete-uri-test");
        ThirdPartyApp saved = thirdPartyAppRepo.save(app);
        entityManager.flush();

        ThirdPartyAppRedirectUri uri = new ThirdPartyAppRedirectUri();
        uri.setAppId(saved.getId());
        uri.setUri("https://client.example.com/to-delete");
        thirdPartyAppRedirectUriRepo.save(uri);
        entityManager.flush();
        entityManager.clear();

        // Pre-condition: URI 已持久化
        assertThat(thirdPartyAppRedirectUriRepo.findByAppId(saved.getId())).hasSize(1);

        // When
        thirdPartyAppRedirectUriRepo.deleteByAppId(saved.getId());
        entityManager.flush();
        entityManager.clear();

        // Then
        assertThat(thirdPartyAppRedirectUriRepo.findByAppId(saved.getId())).isEmpty();
    }

    /**
     * 验证 {@link ThirdPartyAppPostLogoutRedirectUriRepo#findByAppId(Long)} 能返回已持久化的登出后重定向 URI 列表。
     */
    @Test
    void shouldFindPostLogoutRedirectUrisByAppId() {
        // Given
        ThirdPartyApp app = new ThirdPartyApp();
        app.setName("find-post-logout-uri-test");
        ThirdPartyApp saved = thirdPartyAppRepo.save(app);
        entityManager.flush();

        ThirdPartyAppPostLogoutRedirectUri uri1 = new ThirdPartyAppPostLogoutRedirectUri();
        uri1.setAppId(saved.getId());
        uri1.setUri("https://client.example.com/post-logout1");

        ThirdPartyAppPostLogoutRedirectUri uri2 = new ThirdPartyAppPostLogoutRedirectUri();
        uri2.setAppId(saved.getId());
        uri2.setUri("https://client.example.com/post-logout2");

        thirdPartyAppPostLogoutRedirectUriRepo.save(uri1);
        thirdPartyAppPostLogoutRedirectUriRepo.save(uri2);
        entityManager.flush();
        entityManager.clear();

        // When
        List<ThirdPartyAppPostLogoutRedirectUri> found = thirdPartyAppPostLogoutRedirectUriRepo.findByAppId(saved.getId());

        // Then
        assertThat(found).hasSize(2);
        assertThat(found)
                .extracting(ThirdPartyAppPostLogoutRedirectUri::getUri)
                .containsExactlyInAnyOrder(
                        "https://client.example.com/post-logout1",
                        "https://client.example.com/post-logout2"
                );
    }

    /**
     * 验证 {@link ThirdPartyAppPostLogoutRedirectUriRepo#deleteByAppId(Long)} 仅删除指定应用的登出后重定向 URI，不影响其他应用。
     */
    @Test
    void shouldDeletePostLogoutRedirectUrisByAppId() {
        // Given
        ThirdPartyApp app1 = new ThirdPartyApp();
        app1.setName("delete-post-logout-uri-app1");
        ThirdPartyApp saved1 = thirdPartyAppRepo.save(app1);

        ThirdPartyApp app2 = new ThirdPartyApp();
        app2.setName("delete-post-logout-uri-app2");
        ThirdPartyApp saved2 = thirdPartyAppRepo.save(app2);
        entityManager.flush();

        ThirdPartyAppPostLogoutRedirectUri uri1 = new ThirdPartyAppPostLogoutRedirectUri();
        uri1.setAppId(saved1.getId());
        uri1.setUri("https://client.example.com/post-logout-app1");
        thirdPartyAppPostLogoutRedirectUriRepo.save(uri1);

        ThirdPartyAppPostLogoutRedirectUri uri2 = new ThirdPartyAppPostLogoutRedirectUri();
        uri2.setAppId(saved2.getId());
        uri2.setUri("https://client.example.com/post-logout-app2");
        thirdPartyAppPostLogoutRedirectUriRepo.save(uri2);
        entityManager.flush();
        entityManager.clear();

        // Pre-condition: 两个 app 的 URI 都已持久化
        assertThat(thirdPartyAppPostLogoutRedirectUriRepo.findByAppId(saved1.getId())).hasSize(1);
        assertThat(thirdPartyAppPostLogoutRedirectUriRepo.findByAppId(saved2.getId())).hasSize(1);

        // When
        thirdPartyAppPostLogoutRedirectUriRepo.deleteByAppId(saved1.getId());
        entityManager.flush();
        entityManager.clear();

        // Then
        assertThat(thirdPartyAppPostLogoutRedirectUriRepo.findByAppId(saved1.getId())).isEmpty();
        assertThat(thirdPartyAppPostLogoutRedirectUriRepo.findByAppId(saved2.getId())).hasSize(1);
    }

}