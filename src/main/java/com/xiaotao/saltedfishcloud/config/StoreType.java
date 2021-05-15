package com.xiaotao.saltedfishcloud.config;

public enum StoreType {
    RAW,UNIQUE;
    public static String getConfigKey() {
        return "STORE_TYPE";
    }
}
