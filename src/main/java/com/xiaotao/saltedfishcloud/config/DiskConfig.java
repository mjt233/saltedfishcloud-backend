package com.xiaotao.saltedfishcloud.config;


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
public class DiskConfig {
    // 公共网盘路径
    public static String PUBLIC_ROOT;

    // 个人用户数据路径（包括网盘文件，用户配置文件）
    public static String PRIVATE_ROOT;

    @Value("${public.root}")
    public void setRoot(String root) {
        File file = new File(root);
        DiskConfig.PUBLIC_ROOT =file.getPath();
    }

    @Value("${private.root}")
    public void setPublicRoot(String root) {
        File file = new File(root);
        DiskConfig.PRIVATE_ROOT =file.getPath();
    }

    /**
     * 获取用户的私人网盘根目录
     * @return 本地文件目录
     */
    public static String getUserPrivatePath() {
        return DiskConfig.PRIVATE_ROOT + "/" + SecurityContextHolder.getContext().getAuthentication().getName();
    }
}
