package com.xiaotao.saltedfishcloud.dao.jpa;

import com.xiaotao.saltedfishcloud.model.po.OAuth2AuthorizationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

/**
 * {@link OAuth2AuthorizationEntity} 的 JPA Repository。
 * <p>
 * 为 JpaOAuth2AuthorizationService 提供按各类 token 值查找 authorization 的能力。
 * </p>
 */
public interface Oauth2AuthorizationRepo extends JpaRepository<OAuth2AuthorizationEntity, String> {

    /**
     * 按 authorization code 值查找。
     *
     * @param value authorization code 字符串
     * @return 匹配的实体
     */
    Optional<OAuth2AuthorizationEntity> findByAuthorizationCodeValue(String value);

    /**
     * 按 access token 值查找。
     *
     * @param value access token 字符串
     * @return 匹配的实体
     */
    Optional<OAuth2AuthorizationEntity> findByAccessTokenValue(String value);

    /**
     * 按 id token 值查找。
     *
     * @param value id token 字符串
     * @return 匹配的实体
     */
    Optional<OAuth2AuthorizationEntity> findByOidcIdTokenValue(String value);

    /**
     * 按 refresh token 值查找。
     *
     * @param value refresh token 字符串
     * @return 匹配的实体
     */
    Optional<OAuth2AuthorizationEntity> findByRefreshTokenValue(String value);

    /**
     * 按 state 查找。
     *
     * @param state state 字符串
     * @return 匹配的实体
     */
    Optional<OAuth2AuthorizationEntity> findByState(String state);

    /**
     * 按 user code 值查找（Device Flow）。
     *
     * @param value user code 字符串
     * @return 匹配的实体
     */
    Optional<OAuth2AuthorizationEntity> findByUserCodeValue(String value);

    /**
     * 按 device code 值查找（Device Flow）。
     *
     * @param value device code 字符串
     * @return 匹配的实体
     */
    Optional<OAuth2AuthorizationEntity> findByDeviceCodeValue(String value);

    /**
     * 按多种 token 值综合查找（用于未知 token 类型场景）。
     *
     * @param token 要匹配的 token 值，会依次与 state、authorizationCode、accessToken、oidcIdToken、
     *              refreshToken、userCode、deviceCode 比较
     * @return 匹配的实体
     */
    @Query("""
            SELECT a FROM OAuth2AuthorizationEntity a WHERE a.state = :token
            OR a.authorizationCodeValue = :token
            OR a.accessTokenValue = :token
            OR a.oidcIdTokenValue = :token
            OR a.refreshTokenValue = :token
            OR a.userCodeValue = :token
            OR a.deviceCodeValue = :token
            """)
    Optional<OAuth2AuthorizationEntity> findByUnknowTypeToken(String token);
}
