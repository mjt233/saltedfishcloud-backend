package com.xiaotao.saltedfishcloud.utils;

import com.xiaotao.saltedfishcloud.config.security.AllowAnonymous;
import com.xiaotao.saltedfishcloud.entity.po.User;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import javax.annotation.Resource;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 安全与哈希相关的工具类
 */
@Component
public class SecureUtils {
    @Resource
    private RequestMappingHandlerMapping requestMappingHandlerMapping;

    final static private String SALT = "1145141919810";

    /**
     * 随机生成一个UUID<br>
     * 注意：UUID
     */
    static public String getUUID() {
        return UUID.randomUUID().toString().replaceAll("-", "");
    }

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

    /**
     * 获取已注册的控制器路由中允许匿名访问的URL
     * @return 允许匿名访问的URL
     */
    public String[] getAnonymousUrls() {
        Map<RequestMappingInfo, HandlerMethod> handlerMethods = requestMappingHandlerMapping.getHandlerMethods();
        Set<String> res = new HashSet<>();
        handlerMethods.forEach((info, method) -> {
            AllowAnonymous an = method.getMethod().getAnnotation(AllowAnonymous.class);
            if (an != null) {
                res.addAll(info.getPatternValues());
            }
        });
        return res.toArray(new String[0]);
    }
}
