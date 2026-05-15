package com.xiaotao.saltedfishcloud.model.po;

import com.xiaotao.saltedfishcloud.model.template.AuditModel;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * 第三方OAuth应用签发的 ApiTicket 记录。
 */
@Entity
@Data
@EqualsAndHashCode(callSuper = true)
@Table(indexes = {
        @Index(name = "idx_oauth_api_ticket_app_uid", columnList = "appId,uid"),
        @Index(name = "idx_oauth_api_ticket_app_uid_type", columnList = "appId,uid,permanent,revoked"),
        @Index(name = "idx_oauth_api_ticket_jti", columnList = "jti", unique = true)
})
public class ThirdPartyAppApiTicket extends AuditModel {

    /**
     * 第三方OAuth应用id
     */
    private Long appId;

    /**
     * JWT载荷中的唯一票据标识
     */
    private Long jti;

    /**
     * 是否为永久有效的 ApiTicket
     */
    private Boolean permanent;

    /**
     * 票据到期时间，为空时表示永久有效
     */
    private Date expiredDate;

    /**
     * 票据是否已被撤销
     */
    private Boolean revoked;
}

