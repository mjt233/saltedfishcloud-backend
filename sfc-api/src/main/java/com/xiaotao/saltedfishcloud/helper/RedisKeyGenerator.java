package com.xiaotao.saltedfishcloud.helper;

import com.xiaotao.saltedfishcloud.cache.CacheKeyPrefixes;
import com.xiaotao.saltedfishcloud.service.mail.MailValidateType;

public class RedisKeyGenerator {
    /**
     * 生成打包下载的RedisKey
     * @param wid   打包码
     */
    public static String getWrapKey(String wid) {
        return CacheKeyPrefixes.WRAP + wid;
    }

    /**
     * 获取邮件注册码key
     * @param email 邮箱
     */
    public static String getRegCodeKey(String email) {
        return CacheKeyPrefixes.REG_MAIL + email;
    }

    /**
     * 获取用户邮箱验证key
     * @param uid       用户ID
     * @param email     待验证邮箱地址
     * @param type      验证类型
     * @return          Redis key，Redis值为验证码
     */
    public static String getUserEmailValidKey(long uid, String email, MailValidateType type) {
        return CacheKeyPrefixes.MAIL_VALIDATE + uid + ":" + type + ":" + email;
    }

    /**
     * 获取临时文件下载链接缓存 key。
     *
     * @param token 临时授权码
     * @return Redis key
     */
    public static String getFileLinkKey(String token) {
        return CacheKeyPrefixes.FILE_LINK + token;
    }
}
