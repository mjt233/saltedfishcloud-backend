package com.xiaotao.saltedfishcloud.enums;

import com.xiaotao.saltedfishcloud.service.config.ConfigName;

public enum StoreMode {
    RAW,UNIQUE;
    public static ConfigName getConfigKey() {
        return ConfigName.STORE_MODE;
    }
}
