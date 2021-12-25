package com.xiaotao.saltedfishcloud.service.mail;

import lombok.Data;

/**
 * 邮件发信服务器配置选项
 */
@Data
public class MailProperties {
    private String protocol = "smtp";
    private String username;
    private String password;
    private Integer port = 25;
    private String host;
}
