package com.sfc.ext.webdav.model.property;

import com.xiaotao.saltedfishcloud.annotations.ConfigProperty;
import com.xiaotao.saltedfishcloud.annotations.ConfigPropertyEntity;
import lombok.Data;


@Data
@ConfigPropertyEntity(prefix = "webdav")
public class WebDavProperty {
    @ConfigProperty(title = "功能开关", describe = "开启 WebDAV 服务", inputType = "switch", defaultValue = "false")
    private Boolean isEnable = false;

    @ConfigProperty(title = "监听地址", describe = "WebDAV 服务器实际监听的地址，可为空")
    private String listenIp = "";

    @ConfigProperty(title = "监听端口", required = true, describe = "WebDAV 服务器实际监听的端口", defaultValue = "8086")
    private Integer listenPort = 8086;

    @ConfigProperty(title = "展示的服务地址", describe = "仅用于用户查看 WebDAV 信息配置页面中显示的地址。当通过其他Web服务反向代理原始的WebDAV服务后，引导用户访问经过反代后暴露的地址")
    private String displayUrl;

    @ConfigProperty(title = "匿名访问", defaultValue = "false", inputType = "switch", describe = "允许匿名访问(Windows资源管理器不受控)")
    private Boolean isAllowAnonymous = false;
}
