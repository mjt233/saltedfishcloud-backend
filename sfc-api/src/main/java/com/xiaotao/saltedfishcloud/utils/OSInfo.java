package com.xiaotao.saltedfishcloud.utils;

public class OSInfo {
    private static final boolean flag = System.getProperty("os.name").toLowerCase().contains("windows");
    private static final String encoding = System.getProperty("file.encoding");
    private static final String OSDefaultEncoding = isWindows() ? "gbk" : "utf-8";

    public static boolean isWindows() {
        return flag;
    }

    /**
     * 获取虚拟机的编码格式 可由-Dfile.encoding参数指定
     */
    public static String getEncoding() {
        return encoding;
    }

    /**
     * 获取操作系统一般情况下默认的编码，Linux是utf-8，Windows是gbk
     */
    public static String getOSDefaultEncoding() {
        return OSDefaultEncoding;
    }
}
