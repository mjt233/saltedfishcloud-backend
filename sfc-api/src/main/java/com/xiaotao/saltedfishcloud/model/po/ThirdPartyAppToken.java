package com.xiaotao.saltedfishcloud.model.po;

import com.xiaotao.saltedfishcloud.model.template.AuditModel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Entity
@Data
@EqualsAndHashCode(callSuper = true)
@Table(indexes = {
        @Index(name = "idx_uid_appid", columnList = "appId,uid", unique = true),
        @Index(name = "idx_token", columnList = "accessToken", unique = true)
})
public class ThirdPartyAppToken extends AuditModel {

    /**
     * 第三方OAuth应用id
     */
    private Long appId;

    /**
     * 临时API凭证
     */
    @Column(length = 1024)
    private String apiTicket;

    /**
     * 永久的token哈希（作为refresh token）
     */
    private String accessToken;
}
