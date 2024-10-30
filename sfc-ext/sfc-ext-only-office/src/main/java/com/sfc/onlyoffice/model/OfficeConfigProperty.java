package com.sfc.onlyoffice.model;

import com.xiaotao.saltedfishcloud.annotations.ConfigPropertiesGroup;
import com.xiaotao.saltedfishcloud.annotations.ConfigProperty;
import com.xiaotao.saltedfishcloud.annotations.ConfigPropertyEntity;
import com.xiaotao.saltedfishcloud.constant.ConfigInputType;
import lombok.Data;
import org.springframework.stereotype.Component;

@Data
@ConfigPropertyEntity(prefix = "onlyoffice", groups = {
        @ConfigPropertiesGroup(id = "common", name = "通用配置"),
        @ConfigPropertiesGroup(id = "documentServer", name = "ONLYOFFICE服务器配置")
})
@Component
public class OfficeConfigProperty {

    @ConfigProperty(group = "common", title = "全局编辑权限控制", describe = "是否启用文档编辑功能", defaultValue = "false", inputType = ConfigInputType.SWITCH)
    private Boolean enableEdit;

    @ConfigProperty(group = "documentServer", title = "ONLYOFFICE文档服务器地址", describe = "用于提供Office文档文件在线预览和编辑功能。示例: http://192.168.1.233")
    private String documentServerHost;

    @ConfigProperty(group = "documentServer",title = "咸鱼云网盘服务地址",describe = "作为ONLYOFFICE的文件存储服务器，用于构造URL给ONLYOFFICE服务器下载文档文件，因此需要确保ONLYOFFICE可以正常访问到该地址。留空则使用原始请求地址。示例 http://192.168.1.233:8087", defaultValue = "")
    private String fileServerHost;

    @ConfigProperty(group = "documentServer",title = "ONLYOFFICE服务器是否启用JWT",describe = "是否启用JWT", inputType = ConfigInputType.SWITCH)
    private Boolean enableJwt;

    @ConfigProperty(group = "documentServer",title = "JWT密钥", describe = "启用JWT时，文档服务器配置的JWT密钥")
    private String JwtSecret;

    @ConfigProperty(group = "documentServer",title = "当文档URL中的SSL证书无效时，是否忽略",describe = "忽略SSL证书错误", defaultValue = "false", inputType = ConfigInputType.SWITCH)
    private Boolean isIgnoreSsl;
}
