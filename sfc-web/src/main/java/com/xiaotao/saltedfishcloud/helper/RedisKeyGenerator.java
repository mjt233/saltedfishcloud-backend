package com.xiaotao.saltedfishcloud.helper;

public class RedisKeyGenerator {
    public final static String PREFIX = "xyy:";

    /**
     * 生成打包下载的RedisKey
     * @param uid   资源所属的用户ID
     * @param wid   打包码
     */
    public static String getWrapKey(int uid, String wid) {
        return PREFIX + "wrap:" + uid + ":" + wid;
    }
}
