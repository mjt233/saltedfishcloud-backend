package com.xiaotao.saltedfishcloud.config;


import com.xiaotao.saltedfishcloud.utils.SecureUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * 全局配置信息类，用于读取配置文件中的参数
 */
@Component
@PropertySource("classpath:config.properties")
@Slf4j
public class DiskConfig {
    public static final List<String> ACCEPT_AVATAR_TYPE = Arrays.asList("jpg", "jpeg", "gif", "png");

    // 公共网盘路径
    public static String PUBLIC_ROOT;

    // 个人用户数据路径（包括网盘文件，用户配置文件，用户头像等）
    public static String STORE_ROOT;

    // 注册邀请码
    public static String REG_CODE;

    // 用户个性化配置文件根目录
    public static String USER_PROFILE_ROOT;

    @Value("${reg-code}")
    public void setRegCode(String v) {
        log.info("[注册邀请码]" + v);
        REG_CODE = v;
    }

    @Value("${public-root}")
    public void setPublicRoot(String root) {
        log.info("[公共网盘路径]" + root);
        File file = new File(root);
        DiskConfig.PUBLIC_ROOT =file.getPath();
    }

    @Value("${store-root}")
    public void setStoreRoot(String root) {
        log.info("[私人网盘根目录]" + root);
        File file = new File(root);
        DiskConfig.STORE_ROOT =file.getPath();
        DiskConfig.USER_PROFILE_ROOT = STORE_ROOT + "/user_profile/";
    }

    /**
     * 获取当前登录用户的私人网盘根目录（不以/结尾）
     * @return 本地文件目录
     * @throws NullPointerException 未登录
     */
    public static String getLoginUserPrivateDiskRoot() {
        return DiskConfig.STORE_ROOT + "/user_file/" + Objects.requireNonNull(SecureUtils.getSpringSecurityUser()).getUsername();
    }

    /**
     * 获取当前登录用户的个性化配置目录（不以/结尾）
     * @return 本地文件目录
     * @throws NullPointerException 未登录
     */
    public static String getLoginUserProfileRoot() {
        return getUserProfileRoot(Objects.requireNonNull(SecureUtils.getSpringSecurityUser()).getUsername());
    }

    public static String getUserProfileRoot(String username) {
        return DiskConfig.USER_PROFILE_ROOT + "/" + username;
    }

}
