package com.xiaotao.saltedfishcloud.service.mail;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.xiaotao.saltedfishcloud.annotations.*;
import lombok.Data;
import org.springframework.util.StringUtils;

/**
 * 邮件发信服务器配置选项
 */
@Data
@ConfigPropertyEntity(
        groups = {
                @ConfigPropertiesGroup(id = "server", name = "服务器信息"),
                @ConfigPropertiesGroup(id = "ssl", name = "SSL配置"),
                @ConfigPropertiesGroup(id = "sender", name = "发送人信息")
        },
        defaultKeyNameStrategy = ConfigKeyNameStrategy.CAMEL_CASE
)
public class MailProperties {
    /**
     * 发信协议
     */
    @ConfigProperty(title = "发信协议",group = "server")
    private String protocol = "smtp";


    /**
     * 发信协议
     */
    @ConfigProperty(title = "启用SSL", inputType = "switch", group = "ssl", describe = "启用SSL描述")
    private Boolean useSSL = false;

    /**
     * 发信协议
     */
    @ConfigProperty(title = "TLS版本", inputType = "select", group = "ssl", options = {
//            @ConfigSelectOption(title = "TLSv1.0", value = "TLSv1.0"),
//            @ConfigSelectOption(title = "TLSv1.1", value = "TLSv1.1"),
            @ConfigSelectOption(title = "TLSv1.2(默认)", value = "TLSv1.2"),
            @ConfigSelectOption(title = "TLSv1.3", value = "TLSv1.3"),
    })
    private String tlsProtocols = "";

    /**
     * 发信服务器主机名
     */
    @ConfigProperty(title = "发信服务器", group = "server")
    private String host;

    /**
     * 发信服务器端口
     */
    @ConfigProperty(title = "端口", group = "server")
    private Integer port = 25;

    /**
     * 发信人地址
     */
    @ConfigProperty(title = "发信人地址", group = "sender")
    private String from;

    /**
     * 发信人别名称呼
     */
    @ConfigProperty(title = "发信人称呼", group = "sender")
    private String alias;

    /**
     * 发信人回信地址
     */
    @ConfigProperty(title = "回信地址", group = "sender")
    private String reply;


    /**
     * 发信服务器用户名
     */
    @ConfigProperty(title = "用户名", group = "sender")
    private String username;

    /**
     * 发信服务器密码
     */
    @ConfigProperty(title = "密码", group = "sender", isMask = true)
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
