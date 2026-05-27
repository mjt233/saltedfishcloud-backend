package com.xiaotao.saltedfishcloud.service.oidc;

import com.xiaotao.saltedfishcloud.model.po.User;
import com.xiaotao.saltedfishcloud.service.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * {@link OidcUserClaimsMapper} 的纯单元测试。
 * <p>
 * 验证 OIDC 用户信息声明映射器按 scope 正确构建 OIDC claims：
 * <ul>
 *   <li>{@code openid} → {@code sub}（系统 uid 字符串）</li>
 *   <li>{@code profile} → {@code preferred_username}、{@code name}、{@code picture}</li>
 *   <li>{@code email} → {@code email}、{@code email_verified}（始终为 {@code false}）</li>
 *   <li>picture URL 遵循遗留模式：{@code {issuer}/api/user/avatar/{username}?uid={uid}}</li>
 * </ul>
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class OidcUserClaimsMapperTest {

    private static final String ISSUER = "https://cloud.example.com";
    private static final Long UID = 42L;
    private static final String USERNAME = "testuser";
    private static final String EMAIL = "test@example.com";

    @Mock
    private UserService userService;

    private OidcUserClaimsMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new OidcUserClaimsMapper(userService, ISSUER);

        User user = new User();
        user.setId(UID);
        user.setUser(USERNAME);
        user.setEmail(EMAIL);
        when(userService.getUserById(UID)).thenReturn(user);
    }

    /**
     * 验证 {@code openid} scope 产生 {@code sub} 声明，值为 uid 字符串形式。
     */
    @Test
    void buildClaims_withOpenidScope_shouldContainSubAsUidString() {
        OidcUserInfo userInfo = mapper.buildClaims(UID.toString(), Set.of("openid"));

        Map<String, Object> claims = userInfo.getClaims();
        assertEquals(UID.toString(), claims.get("sub"), "sub 应为 uid 的字符串形式");
        assertFalse(claims.containsKey("preferred_username"), "不应包含 profile claims");
        assertFalse(claims.containsKey("email"), "不应包含 email claims");
    }

    /**
     * 验证 {@code profile} scope 产生 {@code preferred_username}、{@code name}、{@code picture} 声明。
     */
    @Test
    void buildClaims_withProfileScope_shouldContainPreferredUsernameAndNameAndPicture() {
        OidcUserInfo userInfo = mapper.buildClaims(UID.toString(), Set.of("openid", "profile"));

        Map<String, Object> claims = userInfo.getClaims();
        assertEquals(USERNAME, claims.get("preferred_username"), "preferred_username 应为用户名");
        assertEquals(USERNAME, claims.get("name"), "name 应为用户名");
        assertNotNull(claims.get("picture"), "应包含 picture 声明");
    }

    /**
     * 验证 picture URL 遵循遗留 open API 模式：{@code {issuer}/api/user/avatar/{username}?uid={uid}}。
     */
    @Test
    void buildClaims_pictureUrlFollowsLegacyAvatarPattern() {
        OidcUserInfo userInfo = mapper.buildClaims(UID.toString(), Set.of("profile"));

        String picture = (String) userInfo.getClaims().get("picture");
        String expectedPicture = ISSUER + "/api/user/avatar/" + USERNAME + "?uid=" + UID;
        assertEquals(expectedPicture, picture, "picture URL 应遵循遗留 avatar 路径模式");
    }

    /**
     * 验证 issuer 末尾有斜杠时，picture URL 不会出现双斜杠。
     */
    @Test
    void buildClaims_issuerWithTrailingSlash_pictureUrlShouldNotHaveDoubleSlash() {
        OidcUserClaimsMapper mapperWithSlash = new OidcUserClaimsMapper(userService, ISSUER + "/");
        OidcUserInfo userInfo = mapperWithSlash.buildClaims(UID.toString(), Set.of("profile"));

        String picture = (String) userInfo.getClaims().get("picture");
        assertFalse(picture.contains("//api/"), "picture URL 不应包含双斜杠");
        assertTrue(picture.startsWith(ISSUER), "picture URL 应以 issuer 开头");
    }

    /**
     * 验证 {@code email} scope 产生 {@code email} 和 {@code email_verified} 声明。
     * {@code email_verified} 始终为 {@code false}，因为系统模型中不存在持久化的邮箱验证标志。
     */
    @Test
    void buildClaims_withEmailScope_shouldContainEmailAndEmailVerifiedFalse() {
        OidcUserInfo userInfo = mapper.buildClaims(UID.toString(), Set.of("openid", "email"));

        Map<String, Object> claims = userInfo.getClaims();
        assertEquals(EMAIL, claims.get("email"), "email 应为用户的绑定邮箱");
        assertEquals(Boolean.FALSE, claims.get("email_verified"),
                "email_verified 应始终为 false（系统无持久化邮箱验证标志）");
    }

    /**
     * 验证授权所有 scope 时，所有相关声明均正确填充。
     */
    @Test
    void buildClaims_withAllScopes_shouldContainAllStandardClaims() {
        OidcUserInfo userInfo = mapper.buildClaims(UID.toString(), Set.of("openid", "profile", "email"));

        Map<String, Object> claims = userInfo.getClaims();
        assertEquals(UID.toString(), claims.get("sub"));
        assertEquals(USERNAME, claims.get("preferred_username"));
        assertEquals(USERNAME, claims.get("name"));
        assertNotNull(claims.get("picture"));
        assertEquals(EMAIL, claims.get("email"));
        assertEquals(Boolean.FALSE, claims.get("email_verified"));
    }

    /**
     * 验证仅授权 {@code email} scope（无 openid/profile）时，仅包含 email 相关声明。
     */
    @Test
    void buildClaims_withOnlyEmailScope_shouldContainOnlyEmailClaims() {
        OidcUserInfo userInfo = mapper.buildClaims(UID.toString(), Set.of("email"));

        Map<String, Object> claims = userInfo.getClaims();
        assertFalse(claims.containsKey("sub"), "未授权 openid 时不应包含 sub");
        assertFalse(claims.containsKey("preferred_username"), "未授权 profile 时不应包含 preferred_username");
        assertTrue(claims.containsKey("email"), "应包含 email");
        assertTrue(claims.containsKey("email_verified"), "应包含 email_verified");
    }
}
