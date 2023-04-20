package com.xiaotao.saltedfishcloud.enums;

import com.sfc.constant.SysConfigName;

public enum StoreMode {
    RAW,UNIQUE;
    public static String getConfigKey() {
        return SysConfigName.Store.SYS_STORE_TYPE;
    }
}
