package com.xiaotao.saltedfishcloud.service.mail;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.xiaotao.saltedfishcloud.annotations.ConfigProperties;
import com.xiaotao.saltedfishcloud.annotations.ConfigPropertiesEntity;
import com.xiaotao.saltedfishcloud.annotations.ConfigPropertiesGroup;
import lombok.Data;
import org.springframework.util.StringUtils;

/**
 * 邮件发信服务器配置选项
 */
@Data
@ConfigPropertiesEntity(
        groups = {
                @ConfigPropertiesGroup(id = "server", name = "服务器信息"),
                @ConfigPropertiesGroup(id = "sender", name = "发送人信息")
        }
)
public class MailProperties {
    /**
     * 发信协议
     */
    @ConfigProperties(value = "发信协议",group = "server")
    private String protocol = "smtp";

    /**
     * 发信服务器主机名
     */
    @ConfigProperties(value = "发信服务器", group = "server")
    private String host;

    /**
     * 发信服务器端口
     */
    @ConfigProperties(value = "端口", group = "server")
    private Integer port = 25;

    /**
     * 发信人地址
     */
    @ConfigProperties(value = "发信人地址", group = "sender")
    private String from;

    /**
     * 发信人别名称呼
     */
    @ConfigProperties(value = "发信人称呼", group = "sender")
    private String alias;

    /**
     * 发信人回信地址
     */
    @ConfigProperties(value = "回信地址", group = "sender")
    private String reply;


    /**
     * 发信服务器用户名
     */
    @ConfigProperties(value = "用户名", group = "sender")
    private String username;

    /**
     * 发信服务器密码
     */
    @ConfigProperties(value = "密码", group = "sender")
    private String password;

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
