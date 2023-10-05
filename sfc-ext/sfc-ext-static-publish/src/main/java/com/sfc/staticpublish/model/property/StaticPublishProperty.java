package com.sfc.staticpublish.model.property;

import com.sfc.constant.ConfigInputType;
import com.xiaotao.saltedfishcloud.annotations.ConfigPropertiesGroup;
import com.xiaotao.saltedfishcloud.annotations.ConfigProperty;
import com.xiaotao.saltedfishcloud.annotations.ConfigPropertyEntity;
import com.xiaotao.saltedfishcloud.annotations.ConfigSelectOption;
import lombok.Data;

@ConfigPropertyEntity(prefix = "static-publish", groups = {
        @ConfigPropertiesGroup(id = "server", name = "服务配置"),
        @ConfigPropertiesGroup(id = "publish", name = "发布配置"),
})
@Data
public class StaticPublishProperty {

    @ConfigProperty(
            group = "server",
            title = "服务端口",
            defaultValue = "9999",
            describe = "静态页面服务需要一个独立的端口来运行http服务器"
    )
    private Integer serverPort;

    @ConfigProperty(
            group = "server",
            title = "服务协议",
            defaultValue = "http",
            inputType = "radio",
            describe = "用于控制在前端生成的站点地址用，不影响实际服务提供的协议。实际服务提供协议目前只支持http，https需要使用其他http中间件（如nginx）代理提供",
            options = {
                    @ConfigSelectOption(title = "HTTP", value = "http"),
                    @ConfigSelectOption(title = "HTTPS", value = "https")
            }
    )
    private String protocol;

    @ConfigProperty(
            group = "publish",
            title = "发布权限控制",
            defaultValue = "true",
            inputType = ConfigInputType.SWITCH,
            describe = "只允许管理员新发布站点"
    )
    private Boolean isOnlyAdminPublish;

    @ConfigProperty(
            group = "publish",
            title = "系统主机后缀",
            defaultValue = "localhost",
            required = true,
            describe = "按主机名发布站点时，静态站点的上级域名，如站点名称为：my-site，系统主机后缀为mydomain.com:6666，则访问域名为：my-site.mydomain.com:6666 \n 优先级高于路径匹配"
    )
    private String byHostSuffix;

    @ConfigProperty(
            group = "publish",
            title = "用户路径主机后缀",
            defaultValue = "localhost",
            required = true,
            describe = "按路径发布站点时，静态站点的上级域名，如站点名称为：my-site，系统主机后缀为mydomain.com:6666，则访问域名为：my-site.mydomain.com:6666"
    )
    private String byPathSuffix;
}
