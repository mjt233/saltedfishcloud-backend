package com.xiaotao.saltedfishcloud.enums;

import com.xiaotao.saltedfishcloud.service.config.SysConfigName;

public enum StoreMode {
    RAW,UNIQUE;
    public static String getConfigKey() {
        return SysConfigName.SYS_STORE_TYPE;
    }
}
