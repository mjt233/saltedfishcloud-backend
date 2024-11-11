package com.sfc.staticpublish.model.property;

import com.xiaotao.saltedfishcloud.constant.ConfigInputType;
import com.xiaotao.saltedfishcloud.annotations.ConfigPropertiesGroup;
import com.xiaotao.saltedfishcloud.annotations.ConfigProperty;
import com.xiaotao.saltedfishcloud.annotations.ConfigPropertyEntity;
import com.xiaotao.saltedfishcloud.annotations.ConfigSelectOption;
import lombok.Data;

@ConfigPropertyEntity(prefix = "static-publish", groups = {
        @ConfigPropertiesGroup(id = "server", name = "服务配置"),
        @ConfigPropertiesGroup(id = "publish", name = "发布配置"),
        @ConfigPropertiesGroup(id = "rootSite", name = "根路径站点配置", describe = "适用于无法自由使用域名的场景下部署站点。如：只在局域网通过IP地址访问服务。\n该功能开启后，根路径站点将作为无匹配站点时的默认回调站点进行路径匹配。")
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


    // todo 系统该设计用户组和权限控制机制了
    @ConfigProperty(
            group = "publish",
            title = "发布权限控制",
            defaultValue = "true",
            inputType = ConfigInputType.SWITCH,
            describe = "只允许管理员管理站点"
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

    @ConfigProperty(
            group = "rootSite",
            title = "功能开关",
            defaultValue = "false",
            describe = "启用根路径站点功能",
            inputType = ConfigInputType.SWITCH
    )
    private Boolean isEnableDirectRootPath;

    @ConfigProperty(
            group = "rootSite",
            title = "服务器站点地址",
            defaultValue = "localhost:9999",
            describe = "静态站点服务器地址，用于按根路径匹配时页面展示URL"
    )
    private String serverAddress;
}
