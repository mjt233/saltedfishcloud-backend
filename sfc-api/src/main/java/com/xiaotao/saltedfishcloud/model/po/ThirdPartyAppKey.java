package com.xiaotao.saltedfishcloud.model.po;

import com.xiaotao.saltedfishcloud.model.template.AuditModel;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 第三方应用密钥凭证
 */
@Entity
@Table(indexes = {
        @Index(name = "idx_client_id", columnList = "appId")
})
@Data
@EqualsAndHashCode(callSuper = true)
public class ThirdPartyAppKey extends AuditModel {
    /**
     * 第三方应用id
     * @see ThirdPartyApp#getId()
     */
    private Long appId;

    /**
     * 客户端密钥的哈希值
     */
    private String clientSecretHash;

    /**
     * 客户端密钥原文的部分遮掩参考值
     */
    private String clientSecretMaskValue;

    /**
     * 名称标记
     */
    private String name;

    /**
     * 备注
     */
    private String remark;
}
