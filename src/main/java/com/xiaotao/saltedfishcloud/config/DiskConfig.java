package com.xiaotao.saltedfishcloud.config;


import com.xiaotao.saltedfishcloud.dao.mybatis.UserDao;
import com.xiaotao.saltedfishcloud.enums.ReadOnlyLevel;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.entity.po.User;
import com.xiaotao.saltedfishcloud.service.config.version.Version;
import com.xiaotao.saltedfishcloud.service.file.path.PathHandler;
import com.xiaotao.saltedfishcloud.service.file.path.RawPathHandler;
import com.xiaotao.saltedfishcloud.service.file.path.UniquePathHandler;
import com.xiaotao.saltedfishcloud.utils.OSInfo;
import com.xiaotao.saltedfishcloud.utils.PathUtils;
import com.xiaotao.saltedfishcloud.utils.SecureUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
 */
@Component
@Slf4j
public class DiskConfig {
    public static Version VERSION;
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

    private static ReadOnlyLevel READ_ONLY_LEVEL = null;


    public static synchronized ReadOnlyLevel getReadOnlyLevel() {
        return READ_ONLY_LEVEL;
    }

    /**
     * 切换系统的只读模式级别<br>
     * NOTE: 只能在只读模式的开与关之间切换，无法从只读模式的某一级别切换到另一级别<br>
     * 例：<br>
     *     null -> DATA_MOVING          [OK]<br>
     *     null -> DATA_CHECKING        [OK]<br>
     *     DATA_MOVING -> null          [OK]<br>
     *     DATA_CHECKING -> null        [OK]<br>
     *     DATA_MOVING -> DATA_CHECKING <strong>[!NO!]</strong> <br>
     *     DATA_CHECKING -> DATA_MOVING <strong>[!NO!]</strong>
     * @param level 只读模式级别
     * @throws IllegalStateException 当系统处于只读模式下抛出此异常
     */
    public static synchronized void setReadOnlyLevel (ReadOnlyLevel level) {
        if (level != null && READ_ONLY_LEVEL != null) {
            throw new IllegalStateException("当前已处于：" + READ_ONLY_LEVEL);
        }
        if (level != null) {
            log.debug("只读级别切换到" + level);
        } else {
            log.debug("关闭只读模式");
        }
        READ_ONLY_LEVEL = level;
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
                throw new JsonException(404, "资源不存在");
            }
        }
    }

    @Value("${app.version}")
    public void setVersion(String v) {
        VERSION = Version.valueOf(v);
    }

    @Value("${reg-code}")
    public void setRegCode(String v) {
        REG_CODE = v;
    }


    @Value("${sync-delay}")
    public void setSyncDelay(int v) {
        SYNC_DELAY = v;
    }

    @Value("${sync-launch}")
    public void setSyncLaunch(boolean a) {
        LAUNCH_SYNC = a;
    }

    @Value("${public-root}")
    public void setPublicRoot(String root) throws IOException {
        if (!OSInfo.isWindows() && !root.startsWith("/"))  {
            throw new IllegalArgumentException("public-root must be start with \"/\" in Linux");
        }
        log.info("[公共网盘路径]" + root);
        File file = new File(root);
        DiskConfig.PUBLIC_ROOT =file.getPath();
        this.checkPathConflict();
    }

    /**
     * 检查公共网盘路径和私人存储路径是否存在冲突。<br>
     * 以下情况会被认为冲突：<br>
     * <ul>
     *  <li>当公共网盘路径或私人网盘路径为父子目录关系</li>
     *  <li>公共网盘路径与私人网盘路径为相同的目录</li>
     * </ul>
     * @throws IOException
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

    @Value("${store-root}")
    public void setStoreRoot(String root) {
        if (!OSInfo.isWindows() && !root.startsWith("/"))  {
            throw new IllegalArgumentException("store-root must be start with \"/\" in Linux");
        }
        log.info("[私人网盘根目录]" + root);
        File file = new File(root);
        DiskConfig.STORE_ROOT =file.getPath();
        DiskConfig.USER_PROFILE_ROOT = STORE_ROOT + "/user_profile/";
        this.checkPathConflict();
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

    /**
     * 获取RAW存储下用户私人网盘根目录
     * @TODO 使用uid替代username
     * @param username  用户名
     */
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
        return rawPathHandler;
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
        return DiskConfig.USER_PROFILE_ROOT + "/" + username;
    }

}
