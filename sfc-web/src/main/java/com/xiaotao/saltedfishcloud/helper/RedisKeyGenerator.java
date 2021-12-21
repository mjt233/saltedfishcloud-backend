package com.xiaotao.saltedfishcloud.helper;

public class RedisKeyGenerator {
    public final static String PREFIX = "xyy:";

    /**
     * 生成打包下载的RedisKey
     * @param wid   打包码
     */
    public static String getWrapKey(String wid) {
        return PREFIX + "wrap:" +wid;
    }
}
