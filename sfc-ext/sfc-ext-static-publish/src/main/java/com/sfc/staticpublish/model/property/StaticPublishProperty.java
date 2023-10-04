package com.sfc.staticpublish.model.property;

import com.sfc.constant.ConfigInputType;
import com.xiaotao.saltedfishcloud.annotations.ConfigProperty;
import com.xiaotao.saltedfishcloud.annotations.ConfigPropertyEntity;
import lombok.Data;

@ConfigPropertyEntity(prefix = "static-publish")
@Data
public class StaticPublishProperty {

    @ConfigProperty(
            title = "发布权限控制",
            defaultValue = "true",
            inputType = ConfigInputType.SWITCH,
            describe = "只允许管理员新发布站点"
    )
    private Boolean isOnlyAdminPublish;

    @ConfigProperty(
            title = "服务端口",
            defaultValue = "9999",
            describe = "静态页面服务需要一个独立的端口来运行http服务器"
    )
    private Integer serverPort;

    @ConfigProperty(
            title = "系统主机后缀",
            defaultValue = "localhost",
            required = true,
            describe = "按主机名发布站点时，静态站点的上级域名，如站点名称为：my-site，系统主机后缀为mydomain.com:6666，则访问域名为：my-site.mydomain.com:6666"
    )
    private String byHostSuffix;

    @ConfigProperty(
            title = "用户路径主机后缀",
            defaultValue = "localhost",
            required = true,
            describe = "按路径发布站点时，静态站点的上级域名，如站点名称为：my-site，系统主机后缀为mydomain.com:6666，则访问域名为：my-site.mydomain.com:6666"
    )
    private String byPathSuffix;
}
