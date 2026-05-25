package com.xiaotao.saltedfishcloud.model.po;

import com.xiaotao.saltedfishcloud.model.template.AuditModel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * OIDC 客户端登出后重定向 URI 实体。
 * <p>
 * 存储第三方应用（{@link ThirdPartyApp}）注册的登出后回调地址列表。
 * 授权服务器在执行 RP-Initiated Logout 流程后，会将用户重定向至此列表中已注册的 URI。
 * </p>
 */
@Entity
@Table(indexes = @Index(name = "idx_oidc_post_logout_redirect_uri_app_id", columnList = "appId"))
@Data
@EqualsAndHashCode(callSuper = true)
public class ThirdPartyAppPostLogoutRedirectUri extends AuditModel {

    /**
     * 所属第三方应用的 ID。
     */
    private Long appId;

    /**
     * 登出后重定向 URI，最大长度 1024 字符。
     */
    @Column(length = 1024, nullable = false)
    private String uri;
}
