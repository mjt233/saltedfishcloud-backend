package com.xiaotao.saltedfishcloud.model.po;

import com.xiaotao.saltedfishcloud.model.template.AuditModel;
import jakarta.persistence.Entity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Entity
@Data
@EqualsAndHashCode(callSuper = true)
public class ThirdPartyAppToken extends AuditModel {

    /**
     * 第三方OAuth应用id
     */
    private Long appId;

    /**
     * 临时API凭证
     */
    private String apiTicket;

    /**
     * 永久的token（作为refresh token）
     */
    private String accessToken;
}
