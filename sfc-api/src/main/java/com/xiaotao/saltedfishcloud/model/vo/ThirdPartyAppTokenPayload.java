package com.xiaotao.saltedfishcloud.model.vo;

import lombok.Data;

@Data
public class ThirdPartyAppTokenPayload {
    private Long appId;
    private Long uid;
    private String scope;
}
