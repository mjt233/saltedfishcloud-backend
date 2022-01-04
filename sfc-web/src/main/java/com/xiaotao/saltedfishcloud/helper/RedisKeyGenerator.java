package com.xiaotao.saltedfishcloud.helper;

import com.xiaotao.saltedfishcloud.service.user.MailValidateType;

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

    /**
     * 获取用户邮箱验证key
     * @param uid       用户ID
     * @param email     待验证邮箱地址
     * @param type      验证类型
     * @return          Redis key，Redis值为验证码
     */
    public static String getUserEmailValidKey(int uid, String email, MailValidateType type) {
        return PREFIX + "mailValidate:" + uid + ":" + type + ":" + email;
    }
}
