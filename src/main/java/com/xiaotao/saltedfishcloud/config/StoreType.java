package com.xiaotao.saltedfishcloud.config;

import com.xiaotao.saltedfishcloud.enums.ConfigName;

import static com.xiaotao.saltedfishcloud.enums.ConfigName.STORE_TYPE;

public enum StoreType {
    RAW,UNIQUE;
    public static ConfigName getConfigKey() {
        return STORE_TYPE;
    }
}
