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

    /**
     * 获取邮件注册码key
     * @param email 邮箱
     */
    public static String getRegCodeKey(String email) {
        return PREFIX + "regMail:" + email;
    }
}
