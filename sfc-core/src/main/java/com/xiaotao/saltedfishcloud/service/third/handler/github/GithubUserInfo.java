package com.xiaotao.saltedfishcloud.service.third.handler.github;

import lombok.Data;

@Data
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
