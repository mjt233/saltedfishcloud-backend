package com.sfc.ext.webrtc.model;

import com.xiaotao.saltedfishcloud.annotations.ConfigPropertiesGroup;
import com.xiaotao.saltedfishcloud.annotations.ConfigProperty;
import com.xiaotao.saltedfishcloud.annotations.ConfigPropertyEntity;
import lombok.Data;

@Data
@ConfigPropertyEntity(prefix = "rtc", groups = {
        @ConfigPropertiesGroup(id = "ice", name = "ICE服务配置")
})
public class RTCProperty {

    @ConfigProperty(title = "是否使用ICE服务器", inputType = "switch", defaultValue = "false", group = "ice", describe = "使用ICE服务器")
    private Boolean useIceServer;

    @ConfigProperty(title = "ICE服务器URL", group = "ice", describe = "支持配置多个URL，用半角英文逗号\",\"分割")
    private String iceServerUrl;
}
