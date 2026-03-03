package com.xiaotao.saltedfishcloud.service.third.handler.github;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;

@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class GithubUserInfo {
    private String login;
    private String name;
    private Long id;
    private String nodeId;
    private String avatarUrl;
    private String gravatarId;
    private String email;
    private String notificationEmail;
}
