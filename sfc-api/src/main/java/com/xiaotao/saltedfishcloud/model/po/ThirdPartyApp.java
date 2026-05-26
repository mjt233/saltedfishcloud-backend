package com.xiaotao.saltedfishcloud.model.po;


import com.xiaotao.saltedfishcloud.constant.ByteSize;
import com.xiaotao.saltedfishcloud.enums.OidcClientType;
import com.xiaotao.saltedfishcloud.enums.OidcTokenEndpointAuthMethod;
import com.xiaotao.saltedfishcloud.model.template.AuditModel;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.validator.constraints.Length;
import org.hibernate.validator.constraints.URL;

/**
 * 第三方平台应用
 */
@Entity
@Table(name = "third_party_app", indexes = {
    @Index(name = "idx_third_party_app_name", columnList = "name", unique = true)
})
@Data
@EqualsAndHashCode(callSuper = true)
public class ThirdPartyApp extends AuditModel {

    /**
     * 应用名称
     */
    @NotBlank
    private String name;

    /**
     * 用户确认授权后的回调URL，可为空（为空时授权接口需要由调用方通过参数传入重定向URL）
     */
    @URL
    @Column(length = 1024)
    private String callbackUrl;

    /**
     * 应用介绍
     */
    @Column(length = 1024)
    private String describeContent;

    /**
     * 联系邮箱
     */
    private String email;

    /**
     * 应用图标(URL 支持base64)
     */
    @Lob
    @Length(max = ByteSize._1KiB * 512)
    private String icon;

    /**
     * 是否已启用
     */
    private Boolean isEnabled;

    /**
     * 是否允许第三方应用申请永久有效的 ApiTicket
     */
    private Boolean allowPermanentApiTicket;

    /**
     * 是否启用 OIDC（OpenID Connect）协议支持。
     */
    @Column(nullable = false)
    private Boolean oidcEnabled = false;

    /**
     * OIDC 客户端类型：机密客户端（CONFIDENTIAL）或公开客户端（PUBLIC）。
     * 默认为机密客户端。
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OidcClientType oidcClientType = OidcClientType.CONFIDENTIAL;

    /**
     * 是否要求客户端使用 PKCE（Proof Key for Code Exchange）流程。
     * 公开客户端建议强制开启。
     */
    @Column(nullable = false)
    private Boolean requirePkce = false;

    /**
     * Token 端点的客户端认证方式。
     * 默认为 HTTP Basic 认证（CLIENT_SECRET_BASIC）。
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OidcTokenEndpointAuthMethod oidcTokenEndpointAuthMethod = OidcTokenEndpointAuthMethod.CLIENT_SECRET_BASIC;
}
