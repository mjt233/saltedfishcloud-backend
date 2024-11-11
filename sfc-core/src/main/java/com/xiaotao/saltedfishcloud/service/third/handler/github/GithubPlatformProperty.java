package com.xiaotao.saltedfishcloud.service.third.handler.github;

import com.xiaotao.saltedfishcloud.annotations.ConfigProperty;
import com.xiaotao.saltedfishcloud.annotations.ConfigPropertyEntity;
import lombok.Data;

@Data
@ConfigPropertyEntity
public class GithubPlatformProperty {
    @ConfigProperty(value = "clientId")
    private String clientId;

    @ConfigProperty(value = "clientSecret")
    private String clientSecret;
}
