package com.xiaotao.saltedfishcloud.config;


import com.xiaotao.saltedfishcloud.dao.UserDao;
import com.xiaotao.saltedfishcloud.exception.HasResultException;
import com.xiaotao.saltedfishcloud.po.User;
import com.xiaotao.saltedfishcloud.service.file.path.PathHandler;
import com.xiaotao.saltedfishcloud.service.file.path.RawPathHandler;
import com.xiaotao.saltedfishcloud.service.file.path.UniquePathHandler;
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
    public static RawPathHandler rawPathHandler;
    public static UniquePathHandler uniquePathHandler;

    private static UserDao userDao;

    public static StoreType STORE_TYPE;

    // 可接收的头像文件后缀名
    public static final List<String> ACCEPT_AVATAR_TYPE = Arrays.asList("jpg", "jpeg", "gif", "png");

    // 同步间隔
    public static int SYNC_DELAY;
    public static boolean LAUNCH_SYNC;  // 启动时同步

    // 公共网盘路径
    public static String PUBLIC_ROOT;

    // 个人用户数据路径（包括网盘文件，用户配置文件，用户头像等）
    public static String STORE_ROOT;

    // 注册邀请码
    public static String REG_CODE;

    // 用户个性化配置文件根目录
    public static String USER_PROFILE_ROOT;

    private static boolean STORE_SWITCHING = false;

    public static boolean isStoreSwitching() {
        return STORE_SWITCHING;
    }

    public static void setStoreSwitching(boolean storeSwitching) {
        STORE_SWITCHING = storeSwitching;
    }

    public DiskConfig(UserDao userDao, RawPathHandler rawPathHandler, UniquePathHandler uniquePathHandler) {
        DiskConfig.userDao = userDao;
        DiskConfig.rawPathHandler = rawPathHandler;
        DiskConfig.uniquePathHandler = uniquePathHandler;
    }

    /**
     * 通过UID获取文件存储的用户根目录，公共用户使用DiskConfig.PUBLIC_ROOT 其他用户使用DiskConfig.PRIVATE_ROOT + "/" + {username} <br>
     * 该目录为原始存储模式下的目录
     * @param uid 用户ID 0表示公共
     * @return 本地文件存储用户根目录，末尾不带/
     */
    static public String getRawFileStoreRootPath(int uid) {
        if (uid == 0) {
            return PUBLIC_ROOT;
        }

        User user = SecureUtils.getSpringSecurityUser();
        if (user != null && uid == user.getId()) {
            return getLoginUserPrivateDiskRoot();
        } else {
            try {
                return getUserPrivateDiskRoot(userDao.getUserById(uid).getUsername());
            } catch (NullPointerException e) {
                throw new HasResultException(404, "资源不存在");
            }
        }
    }

    @Value("${reg-code}")
    public void setRegCode(String v) {
        log.info("[注册邀请码]" + v);
        REG_CODE = v;
    }

    @Value("${sync-delay}")
    public void setSyncDelay(int v) {
        SYNC_DELAY = v;
        log.info("[数据库同步间隔]:" + v + "分钟");
    }

    @Value("${sync-launch}")
    public void setSyncLaunch(boolean a) {
        LAUNCH_SYNC = a;
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

    @Value("${store-type}")
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

    public static String getUserPrivateDiskRoot(String username) {
        return getRawStoreRoot() + username;
    }

    public static String getRawStoreRoot() {
        return DiskConfig.STORE_ROOT + "/user_file/";
    }

    /**
     * 获取唯一文件存储路径
     */
    public static String getUniqueStoreRoot() {
        return DiskConfig.STORE_ROOT + "/repo/";
    }

    /**
     * 获取系统使用的的路径操纵器
     * @return  路径操纵器示例
     */
    public static PathHandler getPathHandler() {
        if (STORE_TYPE == StoreType.RAW) {
            return rawPathHandler;
        } else {
            return uniquePathHandler;
        }
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
