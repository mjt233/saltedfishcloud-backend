package com.xiaotao.saltedfishcloud.config;

import com.xiaotao.saltedfishcloud.service.config.ConfigName;

public enum StoreType {
    RAW,UNIQUE;
    public static ConfigName getConfigKey() {
        return ConfigName.STORE_TYPE;
    }
}
