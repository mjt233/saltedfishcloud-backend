package com.sfc.quickshare.model;

import com.xiaotao.saltedfishcloud.annotations.ConfigProperty;
import com.xiaotao.saltedfishcloud.annotations.ConfigPropertyEntity;
import com.xiaotao.saltedfishcloud.annotations.ConfigPropertiesGroup;
import lombok.Data;

@Data
@ConfigPropertyEntity(
    prefix = QuickShareProperty.CONFIG_PROPERTY_PREFIX,
    groups = {
        @ConfigPropertiesGroup(id="base", name = "基础配置", describe = "文件快速分享插件基础配置")
    }
)
public class QuickShareProperty {
    public final static String CONFIG_PROPERTY_PREFIX = "quickshare";

    @ConfigProperty(describe = "开启快速分享功能", title = "功能开关", inputType = "switch", defaultValue = "true")
    private Boolean isEnabled = Boolean.TRUE;

    @ConfigProperty(describe = "最大文件限制（MiB)", title = "最大文件限制", defaultValue = "512")
    private Long maxSize = 512L;

    @ConfigProperty(describe = "单位：分钟，文件到期后将被自动清理", title = "文件保留时长", defaultValue = "30")
    private int effectiveDuration = 30;
}
