package com.xiaotao.saltedfishcloud.config;

import com.xiaotao.saltedfishcloud.enums.StoreMode;
import com.xiaotao.saltedfishcloud.service.config.ConfigName;
import com.xiaotao.saltedfishcloud.service.config.ConfigService;
import com.xiaotao.saltedfishcloud.service.config.version.Version;
import com.xiaotao.saltedfishcloud.utils.PathUtils;
import lombok.Data;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

@Configuration
@ConfigurationProperties(prefix = "sys")
@Data
public class SysProperties implements InitializingBean {


    @Value("${app.version}")
    private Version version;
    private Common common;
    private Store store;
    private Sync sync;
    private Ftp ftp;

    private ConfigService configService;


    @Data
    public static class Common {

        /**
         * 注册邀请码
         */
        private String regCode = "114514";
    }

    @Data
    public static class Store {

        /**
         * 存储服务类型，可选hdfs或local
         */
        private String type = "local";

        /**
         * 各类资源根目录存储路径
         */
        private String root = "store";

        /**
         * 公共网盘根目录路径
         */
        private String publicRoot = "public";

        /**
         * 存储模式，可选 raw - 原始存储 或 unique - 唯一存储
         */
        private StoreMode mode = StoreMode.RAW;

        public void setMode(String mode) {
            if (mode.toLowerCase().equals("raw")) {
                this.mode = StoreMode.RAW;
            } else {
                this.mode = StoreMode.UNIQUE;
            }
        }
    }

    @Data
    public static class Sync {
        /**
         * 自动同步间隔，单位分钟，负数表示关闭自动同步
         */
        private int interval = -1;

        /**
         * 咸鱼云启动时立即同步，默认关闭
         */
        private boolean syncOnLaunch = false;
    }

    @Data
    public static class Ftp {

        /**
         * 是否启用FTP服务
         */
        private boolean ftpEnable = true;

        /**
         * FTP控制监听地址
         */
        private String listenAddr = "0.0.0.0";

        /**
         * 主控制端口
         */
        private int controlPort = 21;

        /**
         * 被动传输地址
         */
        private String passiveAddr = "localhost";

        /**
         * 被动传输端口范围
         */
        private String passivePort = "20000-30000";
    }

    public void setVersion(String v) {
        version = Version.valueOf(v);
    }

    @Autowired
    public void setConfigService(ConfigService configService) {
        this.configService = configService;
        subscribeConfigureChange();
    }



    private void subscribeConfigureChange() {
        configService.addConfigListener(ConfigName.SYNC_INTERVAL, e -> sync.interval = Integer.parseInt(e));
        configService.addConfigListener(ConfigName.REG_CODE, e -> common.regCode = e);
        configService.addConfigListener(ConfigName.STORE_MODE, e -> {
            final StoreMode storeMode = StoreMode.valueOf(e);
            try {
                configService.setStoreType(storeMode);
                store.mode = storeMode;
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        });
    }

    /*
     * 检查公共网盘路径和私人存储路径是否存在冲突。<br>
     * 以下情况会被认为冲突：<br>
     * <ul>
     *  <li>当公共网盘路径或私人网盘路径为父子目录关系</li>
     *  <li>公共网盘路径与私人网盘路径为相同的目录</li>
     * </ul>
     */
    public void checkPathConflict() {
        if (
                PathUtils.isSubDir(store.publicRoot, store.root) ||
                        PathUtils.isSubDir(store.root, store.publicRoot) ||
                        store.root.equals(store.publicRoot)
        ) {
            throw new IllegalArgumentException("公共网盘路径与私人网盘路径冲突（父子关系或相等）");
        }
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        this.checkPathConflict();
    }
}
