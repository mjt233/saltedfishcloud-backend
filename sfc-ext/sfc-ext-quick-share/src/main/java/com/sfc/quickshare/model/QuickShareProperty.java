package com.sfc.quickshare.model;

import com.xiaotao.saltedfishcloud.annotations.ConfigProperties;
import com.xiaotao.saltedfishcloud.annotations.ConfigPropertiesEntity;
import com.xiaotao.saltedfishcloud.annotations.ConfigPropertiesGroup;
import com.xiaotao.saltedfishcloud.constant.ByteSize;
import lombok.Data;

@Data
@ConfigPropertiesEntity(groups = {
        @ConfigPropertiesGroup(id="base", name = "基础配置", describe = "文件快速分享插件基础配置")
})
public class QuickShareProperty {
    public final static String KEY_IS_ENABLE = "quickshare.is-enable";
    public final static String KEY_MAX_SIZE = "quickshare.max-size";
    public final static String KEY_EFFECTIVE_DURATION = "quickshare.effective-duration";

    @ConfigProperties(value = KEY_IS_ENABLE, describe = "开启快速分享功能", title = "功能开关", inputType = "switch", defaultValue = "true")
    private Boolean isEnabled = Boolean.TRUE;

    @ConfigProperties(value = KEY_MAX_SIZE, describe = "最大文件限制（MiB)", title = "最大文件限制", defaultValue = "512")
    private Long maxSize = 512L;

    @ConfigProperties(value = KEY_EFFECTIVE_DURATION, describe = "单位：分钟，文件到期后将被自动清理", title = "文件保留时长", defaultValue = "30")
    private Long effectiveDuration = 30L;
}
