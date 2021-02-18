package com.xiaotao.saltedfishcloud.utils;

public class OSInfo {
    private static final boolean flag = System.getProperty("os.name").toLowerCase().contains("windows");
    static public boolean isWindows() {
        return flag;
    }
}
