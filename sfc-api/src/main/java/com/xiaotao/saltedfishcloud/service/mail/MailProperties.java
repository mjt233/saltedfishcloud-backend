package com.xiaotao.saltedfishcloud.service.mail;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import org.springframework.util.StringUtils;

/**
 * 邮件发信服务器配置选项
 */
@Data
public class MailProperties {
    /**
     * 发信人地址
     */
    private String from;

    /**
     * 发信人别名称呼
     */
    private String alias;

    /**
     * 发信人回信地址
     */
    private String reply;

    /**
     * 发信协议
     */
    private String protocol = "smtp";

    /**
     * 发信服务器用户名
     */
    private String username;

    /**
     * 发信服务器密码
     */
    private String password;

    /**
     * 发信服务器端口
     */
    private Integer port = 25;

    /**
     * 发信服务器主机名
     */
    private String host;

    /**
     * 判断当前的属性能否满足基本的发信需求（host，from，username不得为空）
     * @return 满足需求返回true，否则为false
     */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    public boolean isValid() {
        return StringUtils.hasText(host) &&
                StringUtils.hasText(from) &&
                StringUtils.hasText(username);
    }
}
