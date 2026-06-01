package com.xiaotao.saltedfishcloud.model.po;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.Instant;

/**
 * Spring Authorization Server {@code oauth2_authorization} 表 JPA 实体。
 * <p>
 * 用于持久化完整的 OAuth2Authorization 状态，
 * 包括所有 token 及属性，使服务重启后 refresh_token 等流程可正常恢复。
 * </p>
 * <p>不继承 {@code BaseModel}/{@code AuditModel}，直接映射 Spring AS 官方表结构。</p>
 */
@Entity
@Table(indexes = {
        @Index(name = "idx_oauth2_authorization_authorization_code_value", columnList = "authorizationCodeValue"),
        @Index(name = "idx_oauth2_authorization_access_token_value", columnList = "accessTokenValue"),
        @Index(name = "idx_oauth2_authorization_oidc_id_token_value", columnList = "oidcIdTokenValue"),
        @Index(name = "idx_oauth2_authorization_refresh_token_value", columnList = "refreshTokenValue"),
        @Index(name = "idx_oauth2_authorization_state", columnList = "state"),
        @Index(name = "idx_oauth2_authorization_user_code_value", columnList = "userCodeValue"),
        @Index(name = "idx_oauth2_authorization_device_code_value", columnList = "deviceCodeValue")
})
@Data
public class OAuth2AuthorizationEntity {

    /**
     * 主键标识，由 Spring Authorization Server 生成的唯一 ID。
     */
    @Id
    @Column(length = 100, nullable = false)
    private String id;

    /**
     * 已注册客户端的 ID，关联客户端注册表。
     */
    @Column(length = 100, nullable = false)
    private String registeredClientId;

    /**
     * 授权主体名称（通常是用户名）。
     */
    @Column(length = 200, nullable = false)
    private String principalName;

    /**
     * 授权类型，例如 {@code authorization_code}、{@code refresh_token} 等。
     */
    @Column(length = 100, nullable = false)
    private String authorizationGrantType;

    /**
     * 授权的权限范围，多个 scope 以空格或逗号分隔。
     */
    @Column(length = 1000)
    private String authorizedScopes;

    /**
     * OAuth2 授权请求的附加属性，JSON 格式。
     */
    @Lob
    @Column
    private String attributes;

    /**
     * 授权码流程中保存的 state 参数，用于 CSRF 防护。
     */
    @Column(length = 500)
    private String state;

    /**
     * 授权码（Authorization Code）的值。
     */
    @Column(length = 1000)
    private String authorizationCodeValue;

    /**
     * 授权码的签发时间。
     */
    @Column
    private Instant authorizationCodeIssuedAt;

    /**
     * 授权码的过期时间。
     */
    @Column
    private Instant authorizationCodeExpiresAt;

    /**
     * 授权码的元数据，JSON 格式。
     */
    @Lob
    @Column
    private String authorizationCodeMetadata;

    /**
     * 访问令牌（Access Token）的值。
     */
    @Column(length = 1000)
    private String accessTokenValue;

    /**
     * 访问令牌的签发时间。
     */
    @Column
    private Instant accessTokenIssuedAt;

    /**
     * 访问令牌的过期时间。
     */
    @Column
    private Instant accessTokenExpiresAt;

    /**
     * 访问令牌的元数据，JSON 格式。
     */
    @Lob
    @Column
    private String accessTokenMetadata;

    /**
     * 访问令牌的类型，例如 {@code Bearer}。
     */
    @Column(length = 100)
    private String accessTokenType;

    /**
     * 访问令牌所包含的权限范围。
     */
    @Column(length = 1000)
    private String accessTokenScopes;

    /**
     * OIDC ID Token 的值。
     */
    @Column(length = 1000)
    private String oidcIdTokenValue;

    /**
     * OIDC ID Token 的签发时间。
     */
    @Column
    private Instant oidcIdTokenIssuedAt;

    /**
     * OIDC ID Token 的过期时间。
     */
    @Column
    private Instant oidcIdTokenExpiresAt;

    /**
     * OIDC ID Token 的元数据，JSON 格式。
     */
    @Lob
    @Column
    private String oidcIdTokenMetadata;

    /**
     * 刷新令牌（Refresh Token）的值。
     */
    @Column(length = 1000)
    private String refreshTokenValue;

    /**
     * 刷新令牌的签发时间。
     */
    @Column
    private Instant refreshTokenIssuedAt;

    /**
     * 刷新令牌的过期时间。
     */
    @Column
    private Instant refreshTokenExpiresAt;

    /**
     * 刷新令牌的元数据，JSON 格式。
     */
    @Lob
    @Column
    private String refreshTokenMetadata;

    /**
     * 用户码（User Code）的值，用于设备授权流程。
     */
    @Column(length = 500)
    private String userCodeValue;

    /**
     * 用户码的签发时间。
     */
    @Column
    private Instant userCodeIssuedAt;

    /**
     * 用户码的过期时间。
     */
    @Column
    private Instant userCodeExpiresAt;

    /**
     * 用户码的元数据，JSON 格式。
     */
    @Lob
    @Column
    private String userCodeMetadata;

    /**
     * 设备码（Device Code）的值，用于设备授权流程。
     */
    @Column(length = 500)
    private String deviceCodeValue;

    /**
     * 设备码的签发时间。
     */
    @Column
    private Instant deviceCodeIssuedAt;

    /**
     * 设备码的过期时间。
     */
    @Column
    private Instant deviceCodeExpiresAt;

    /**
     * 设备码的元数据，JSON 格式。
     */
    @Lob
    @Column
    private String deviceCodeMetadata;
}
