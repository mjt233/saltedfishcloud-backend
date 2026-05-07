package com.xiaotao.saltedfishcloud.utils;

import com.xiaotao.saltedfishcloud.model.po.UserPrincipal;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.DigestUtils;

import java.util.Optional;
import java.util.UUID;

/**
 * 安全与哈希相关的工具类
 */
public class SecureUtils {
    private static final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    final static private String SALT = "1145141919810";

    /**
     * 随机生成一个UUID<br>
     * 注意：该方法返回的UUID不包含横杠
     */
    public static String getUUID() {
        return UUID.randomUUID().toString().replaceAll("-", "");
    }

    /**
     * 由于系统历史数据原因，系统默认的PasswordEncoder是基于MD5的，现在更主流的做法是使用BCrypt或Argon2对密码加密
     */
    public static BCryptPasswordEncoder getBCryptPasswordEncoder() {
        return passwordEncoder;
    }

    /**
     * 取原始密码加盐哈希值
     * @param originPwd 密码原文
     * @return  哈希运算后的结果
     */
    public static String getPassswd(String originPwd) {
        return DigestUtils.md5DigestAsHex((SALT + originPwd).getBytes());
    }

    /**
     * 获取字符串的MD5
     * @param input 输入字符串
     * @return  MD5运算结果
     */
    public static String getMd5(String input) {
        return DigestUtils.md5DigestAsHex(input.getBytes());
    }

    /**
     * 从HTTP Servlet请求对象中获取有效的token凭证字符串
     */
    public static String getToken(HttpServletRequest req) {
        // 先从header获取token
        String token = req.getHeader(JwtUtils.AUTHORIZATION);
        if (token == null) {
            // 获取不到再从表单获取
            token = req.getParameter(JwtUtils.AUTHORIZATION);
        }
        // 最后从cookie中获取
        if (token == null && req.getCookies() != null) {
            for (Cookie cookie : req.getCookies()) {
                if (!"token".equals(cookie.getName())) {
                    continue;
                }
                token = cookie.getValue();
            }
        }
        return token;
    }

    /**
     * 获取SpringSecurity中通过认证的UserPrincipal对象，若无，则返回null
     */
    public static UserPrincipal getSpringSecurityUser() {
        return (UserPrincipal) Optional.ofNullable(SecurityContextHolder.getContext())
                .map(SecurityContext::getAuthentication)
                .map(Authentication::getPrincipal)
                .filter(e -> e instanceof UserPrincipal)
                .orElse(null);
    }

    /**
     * 当前登录用户是否为管理员
     */
    public static boolean currentIsAdmin() {
        return Optional.ofNullable(getSpringSecurityUser())
                .map(UserPrincipal::isAdmin)
                .orElse(false);
    }

    /**
     * 获取当前已登陆用户的id。若未绑定用户则返回null。
     * @return 当前用户id
     */
    public static Long getCurrentUid() {
        return Optional.ofNullable(getSpringSecurityUser())
                .map(UserPrincipal::getId)
                .orElse(null);
    }
    


    /**
     * 将指定的用户对象作为当前操作用户绑定到当前线程上下文
     * @param user  用户
     */
    public static void bindUser(UserPrincipal user) {
        SecurityContextHolder.setContext(new SecurityContextImpl(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities())
        ));
    }

    /**
     * 从当前线程上下文中取消用户绑定
     */
    public static void unbind() {
        SecurityContextHolder.clearContext();
    }

}
