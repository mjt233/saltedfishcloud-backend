package com.xiaotao.saltedfishcloud.enums;

import com.xiaotao.saltedfishcloud.model.config.SysCommonConfig;
import com.sfc.common.utils.PropertyUtils;

public enum StoreMode {
    RAW,UNIQUE;
    public static String getConfigKey() {
        return PropertyUtils.parseLambdaConfigName(SysCommonConfig::getStoreMode);
    }
}
