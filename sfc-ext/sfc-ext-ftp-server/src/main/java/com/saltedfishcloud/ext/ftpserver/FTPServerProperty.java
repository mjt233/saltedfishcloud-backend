package com.saltedfishcloud.ext.ftpserver;

import com.xiaotao.saltedfishcloud.annotations.ConfigProperties;
import com.xiaotao.saltedfishcloud.annotations.ConfigPropertiesEntity;
import com.xiaotao.saltedfishcloud.annotations.ConfigPropertiesGroup;
import com.xiaotao.saltedfishcloud.utils.TypeUtils;
import lombok.Data;

@Data
@ConfigPropertiesEntity(groups = {
        @ConfigPropertiesGroup(id = "base", name = "基本信息"),
        @ConfigPropertiesGroup(id = "passive", name = "被动传输")
})
public class FTPServerProperty {

    /**
     * 是否启用FTP服务
     */
    @ConfigProperties(value = "ftpEnable", title = "是否启用", inputType = "switch", defaultValue = "false")
    private boolean ftpEnable = false;

    /**
     * FTP控制监听地址
     */
    @ConfigProperties(value = "listenAddr", title = "控制端口监听地址", defaultValue = "0.0.0.0", required = true)
    private String listenAddr = "0.0.0.0";

    /**
     * 主控制端口
     */
    @ConfigProperties(value = "controlPort", title = "主控制端口", defaultValue = "2121", describe = "用于连接控制的端口")
    private int controlPort = 2121;

    /**
     * 被动传输地址
     */
    @ConfigProperties(value = "passiveAddr", title = "被动传输地址", defaultValue = "localhost",describe = "被动模式下客户端使用连接传输数据的地址", group = "passive")
    private String passiveAddr = "localhost";

    /**
     * 被动传输端口范围
     */
    @ConfigProperties(
            value = "passivePort",
            title = "被动传输端口范围",
            defaultValue = "20000-30000",
            describe = "被动模式下服务器开放的数据传输端口范围",
            group = "passive"
    )
    private String passivePort = "20000-30000";

    public void setFtpEnable(Object ftpEnable) {
        this.ftpEnable = TypeUtils.toBoolean(ftpEnable);
    }

    public void setControlPort(Object controlPort) {
        this.controlPort = TypeUtils.toNumber(Integer.class, controlPort);
    }
}
