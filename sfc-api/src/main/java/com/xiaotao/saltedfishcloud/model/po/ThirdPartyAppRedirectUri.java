package com.xiaotao.saltedfishcloud.model.po;

import com.xiaotao.saltedfishcloud.model.template.AuditModel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * OIDC 客户端重定向 URI 实体。
 * <p>
 * 存储第三方应用（{@link ThirdPartyApp}）注册的 OIDC 授权回调地址列表。
 * 授权服务器在颁发授权码或令牌时，会验证 redirect_uri 参数必须在此列表中注册。
 * </p>
 */
@Entity
@Table(indexes = @Index(name = "idx_oidc_redirect_uri_app_id", columnList = "appId"))
@Data
@EqualsAndHashCode(callSuper = true)
public class ThirdPartyAppRedirectUri extends AuditModel {

    /**
     * 所属第三方应用的 ID。
     */
    @Column(nullable = false)
    private Long appId;

    /**
     * 重定向 URI，最大长度 1024 字符。
     */
    @Column(length = 1024, nullable = false)
    private String uri;
}
