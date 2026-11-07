package com.xiaotao.saltedfishcloud.service.third.handler.github;

import com.sfc.common.annotations.ConfigProperty;
import com.sfc.common.annotations.ConfigPropertyEntity;
import lombok.Data;

@Data
@ConfigPropertyEntity
public class GithubPlatformProperty {
    @ConfigProperty(value = "clientId")
    private String clientId;

    @ConfigProperty(value = "clientSecret")
    private String clientSecret;
}
