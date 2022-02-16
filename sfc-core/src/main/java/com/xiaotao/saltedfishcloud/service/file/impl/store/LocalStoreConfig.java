package com.xiaotao.saltedfishcloud.service.file.impl.store;


import com.xiaotao.saltedfishcloud.config.StoreType;
import com.xiaotao.saltedfishcloud.dao.mybatis.UserDao;
import com.xiaotao.saltedfishcloud.entity.po.User;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.utils.OSInfo;
import com.xiaotao.saltedfishcloud.utils.PathUtils;
import com.xiaotao.saltedfishcloud.utils.SecureUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * 全局配置信息类，用于读取配置文件中的参数
 * @TODO 抽离依赖其他数据的操作方法到其他类，按单一职责原则重构
 */
@Component
@PropertySource("classpath:config.properties")
@Slf4j
public class LocalStoreConfig {

    public static StoreType STORE_TYPE;


    // 同步间隔
    public static int SYNC_DELAY;
    public static boolean LAUNCH_SYNC;  // 启动时同步

    // 公共网盘路径
    public static String PUBLIC_ROOT;

    // 个人用户数据路径（包括网盘文件，用户配置文件，用户头像等）
    public static String STORE_ROOT;

    // 用户个性化配置文件根目录
    public static String USER_PROFILE_ROOT;





    @Value("${local.store.sync-delay}")
    public void setSyncDelay(int v) {
        SYNC_DELAY = v;
    }

    @Value("${local.store.sync-launch}")
    public void setSyncLaunch(boolean a) {
        LAUNCH_SYNC = a;
    }

    @Value("${local.store.public-root}")
    public void setPublicRoot(String root) throws IOException {
        if (!OSInfo.isWindows() && !root.startsWith("/"))  {
            throw new IllegalArgumentException("public-root must be start with \"/\" in Linux");
        }
        File file = new File(root);
        LocalStoreConfig.PUBLIC_ROOT =file.getPath();
        this.checkPathConflict();
    }

    /**
     * 检查公共网盘路径和私人存储路径是否存在冲突。<br>
     * 以下情况会被认为冲突：<br>
     * <ul>
     *  <li>当公共网盘路径或私人网盘路径为父子目录关系</li>
     *  <li>公共网盘路径与私人网盘路径为相同的目录</li>
     * </ul>
     */
    private void checkPathConflict() {
        if (STORE_ROOT == null || PUBLIC_ROOT == null) return;
        Path pub = Paths.get(PUBLIC_ROOT);
        Path sto = Paths.get(STORE_ROOT);
        if (
            PathUtils.isSubDir(PUBLIC_ROOT, STORE_ROOT) ||
            PathUtils.isSubDir(STORE_ROOT, PUBLIC_ROOT) ||
            pub.equals(sto)
        ) {
            throw new IllegalArgumentException("公共网盘路径与私人网盘路径冲突（父子关系或相等）");
        }
    }

    @Value("${local.store.store-root}")
    public void setStoreRoot(String root) {
        if (!OSInfo.isWindows() && !root.startsWith("/"))  {
            throw new IllegalArgumentException("store-root must be start with \"/\" in Linux");
        }
        File file = new File(root);
        LocalStoreConfig.STORE_ROOT =file.getPath();
        LocalStoreConfig.USER_PROFILE_ROOT = STORE_ROOT + "/user_profile/";
        this.checkPathConflict();
    }

    @Value("${local.store.store-type}")
    public void setStoreType(String type) {
        if (type.toLowerCase().equals("raw")) {
            STORE_TYPE = StoreType.RAW;
        } else {
            STORE_TYPE = StoreType.UNIQUE;
        }
    }

    /**
     * 获取当前登录用户的私人网盘根目录（不以/结尾）
     * @return 本地文件目录
     * @throws NullPointerException 未登录
     */
    public static String getLoginUserPrivateDiskRoot() {
        return getUserPrivateDiskRoot(Objects.requireNonNull(SecureUtils.getSpringSecurityUser()).getUsername());
    }

    /**
     * 获取RAW存储下用户私人网盘根目录
     * @TODO 使用uid替代username
     * @param username  用户名
     */
    public static String getUserPrivateDiskRoot(String username) {
        return getRawStoreRoot() + username;
    }

    public static String getRawStoreRoot() {
        return LocalStoreConfig.STORE_ROOT + "/user_file/";
    }

    /**
     * 获取唯一文件存储路径
     */
    public static String getUniqueStoreRoot() {
        return LocalStoreConfig.STORE_ROOT + "/repo/";
    }


    /**
     * 获取当前登录用户的个性化配置目录（不以/结尾）
     * @return 本地文件目录
     * @throws NullPointerException 未登录
     */
    public static String getLoginUserProfileRoot() {
        return getUserProfileRoot(Objects.requireNonNull(SecureUtils.getSpringSecurityUser()).getUsername());
    }

    /**
     * 获取用户profile根
     * @TODO 使用uid替代username
     * @param username  用户名
     * @return  本次文件系统路径
     */
    public static String getUserProfileRoot(String username) {
        return LocalStoreConfig.USER_PROFILE_ROOT + "/" + username;
    }

}
