package com.xiaotao.saltedfishcloud.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ThirdPartyAppApiTicketPayload {
    private Long appId;
    private Long uid;
    private String scope;

    /**
     * 预留的JWT id标识，暂时用不上
     */
    private Long jti;
}
