package com.sfc.ext.webdav.model.property;

import com.xiaotao.saltedfishcloud.annotations.ConfigProperty;
import com.xiaotao.saltedfishcloud.annotations.ConfigPropertyEntity;
import lombok.Data;


@Data
@ConfigPropertyEntity(prefix = "webdav")
public class WebDavProperty {
    @ConfigProperty(title = "功能开关", describe = "开启 WebDAV", inputType = "switch", defaultValue = "false")
    private Boolean isEnable = false;

    @ConfigProperty(title = "服务端口", defaultValue = "8086")
    private Integer serverPort = 8086;

    @ConfigProperty(title = "匿名访问", defaultValue = "false", inputType = "switch", describe = "允许匿名访问(Windows资源管理器不受控)")
    private Boolean isAllowAnonymous = false;
}
