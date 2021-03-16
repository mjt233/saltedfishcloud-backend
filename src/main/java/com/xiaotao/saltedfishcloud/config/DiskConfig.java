package com.xiaotao.saltedfishcloud.config;


import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.PropertySource;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.io.File;

/**
 * 全局配置信息类，用于读取配置文件中的参数
 */
@Component
@PropertySource("classpath:config.properties")
@Slf4j
public class DiskConfig {
    // 公共网盘路径
    public static String PUBLIC_ROOT;

    // 个人用户数据路径（包括网盘文件，用户配置文件，用户头像等）
    public static String STORE_ROOT;

    public static String REG_CODE;

    @Value("${reg-code}")
    public void setRegCode(String v) {
        log.info("[注册邀请码]" + v);
        REG_CODE = v;
    }

    @Value("${public-root}")
    public void setRoot(String root) {
        log.info("[公共网盘路径]" + root);
        File file = new File(root);
        DiskConfig.PUBLIC_ROOT =file.getPath();
    }

    @Value("${store-root}")
    public void setPublicRoot(String root) {
        log.info("[私人网盘根目录]" + root);
        File file = new File(root);
        DiskConfig.STORE_ROOT =file.getPath();
    }

    /**
     * 获取用户的私人网盘根目录（不以/结尾）
     * @return 本地文件目录
     */
    public static String getUserPrivatePath() {
        return DiskConfig.STORE_ROOT + "/user_file/" + SecurityContextHolder.getContext().getAuthentication().getName();
    }
}
