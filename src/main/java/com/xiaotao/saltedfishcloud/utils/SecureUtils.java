package com.xiaotao.saltedfishcloud.utils;

import com.xiaotao.saltedfishcloud.po.User;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.DigestUtils;

import java.util.Date;

/**
 * 安全与哈希相关的工具类
 */
public class SecureUtils {
    final static private String SALT = "1145141919810";
    final static private String TOKEN_SALT = "henghenghengeaaaaaaaa";

    /**
     * 取原始密码加盐哈希值
     * @param originPwd 密码原文
     * @return  哈希运算后的结果
     */
    static public String getPassswd(String originPwd) {
        return DigestUtils.md5DigestAsHex((SALT + originPwd).getBytes());
    }

    /**
     * 获取字符串的MD5
     * @param input 输入字符串
     * @return  MD5运算结果
     */
    static public String getMd5(String input) {
        return DigestUtils.md5DigestAsHex(input.getBytes());
    }

    /**
     * 获取SpringSecurity中通过认证的User对象，若无，则返回null
     * @return User对象
     */
    static public User getSpringSecurityUser() {
        try {
            return (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        } catch (Exception e) {
            return null;
        }
    }
}
