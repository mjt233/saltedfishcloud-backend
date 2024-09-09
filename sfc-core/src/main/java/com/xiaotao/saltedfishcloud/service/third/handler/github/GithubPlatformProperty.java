package com.xiaotao.saltedfishcloud.service.third.handler.github;

import com.xiaotao.saltedfishcloud.annotations.ConfigPropertyEntity;
import lombok.Data;

@Data
@ConfigPropertyEntity
public class GithubPlatformProperty {
    private String clientId;
    private String clientSecret;
}
