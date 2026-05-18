package com.xiaotao.saltedfishcloud.model.po;

import com.xiaotao.saltedfishcloud.model.template.AuditModel;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

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
     * Access Token Sha256 的 BCrypt 值。
     * <p>
     * Access Token 原文可能较长，因此会先计算固定长度摘要，再执行 BCrypt 后持久化。
     */
    private String accessToken;

    /**
     * Access Token 到期日期
     */
    private Date accessTokenExpiredDate;
}
