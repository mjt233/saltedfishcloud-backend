package com.sfc.ext.webdav.store.model;

import com.xiaotao.saltedfishcloud.annotations.*;
import lombok.Data;

@Data
@ConfigPropertyEntity(
        groups = {
                @ConfigPropertiesGroup(id = "base", name = "服务器信息"),
                @ConfigPropertiesGroup(id = "auth", name = "认证信息"),
                @ConfigPropertiesGroup(id = "security", name = "安全选项")
        },
        defaultKeyNameStrategy = ConfigKeyNameStrategy.CAMEL_CASE
)
public class WebDavClientProperty {
    @ConfigProperty(title = "协议", defaultValue = "https", inputType = "select", options = {
            @ConfigSelectOption(title = "HTTP", value = "http"),
            @ConfigSelectOption(title = "HTTPS", value = "https")
    })
    private String protocol;

    @ConfigProperty(title = "主机", required = true)
    private String host;

    @ConfigProperty(title = "目标目录")
    private String basePath;

    @ConfigProperty(group = "auth", title = "用户名")
    private String username;

    @ConfigProperty(group = "auth", title = "密码", isMask = true)
    private String password;

    @ConfigProperty(group = "auth", title = "匿名登录", describe = "匿名登录", inputType = "switch", defaultValue = "false")
    private Boolean isAnonymous;

    @ConfigProperty(group = "security", title = "只读模式", describe = "只读模式", inputType = "switch", defaultValue = "false")
    private Boolean isReadOnly;
}
