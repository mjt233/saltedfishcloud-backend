package com.xiaotao.saltedfishcloud.model.vo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Date;

@Data
public class ThirdPartyAppKeyVo {
    private Long id;

    private Date createAt;

    private Date updateAt;

    private Long uid;

    @NotNull
    private Long appId;

    private String clientSecret;

    private String name;

    private String remark;
}
