package com.xiaotao.saltedfishcloud.utils;

import com.xiaotao.saltedfishcloud.po.User;
import org.springframework.context.annotation.Bean;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.DigestUtils;

import java.util.Date;

public class SecureUtils {
    final static private String SALT = "1145141919810";
    final static private String TOKEN_SALT = "henghenghengeaaaaaaaa";
    static public String getPassswd(String originPwd) {
        return DigestUtils.md5DigestAsHex((SALT + originPwd).getBytes());
    }
    static public String getToken(String pwdMd5) {
        return DigestUtils.md5DigestAsHex((TOKEN_SALT + pwdMd5).getBytes()) + new Date().getTime();
    }

    static public String getMd5(String input) {
        return DigestUtils.md5DigestAsHex(input.getBytes());
    }

    static public User getSpringSecurityUser() {
        try {
            return (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        } catch (Exception e) {
            return null;
        }
    }
}
