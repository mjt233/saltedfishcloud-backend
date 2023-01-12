package com.xiaotao.saltedfishcloud.constant;

// @TODO
public interface SysConfigName {

    /**
     * 老配置中的系统版本
     */
    String OLD_VERSION = "VERSION";

    /**
     * 存储配置
     */
    interface Store {

        /**
         * 存储模式
         */
        String SYS_STORE_TYPE = "sys.store.mode";
        /**
         * 自动同步间隔
         */
        String SYNC_INTERVAL = "sys.store.sync_interval";
    }

    interface Bg {
        /**
         * 主背景图配置
         */
        String SYS_BG_MAIN = "sys.bg.main";
    }

    interface Theme {

        /**
         * 默认黑色主题
         */
        String DARK = "sys.theme.dark";
    }

    /**
     * 常规设置
     */
    interface Common {

        /**
         * 系统版本
         */
        String VERSION = "sys.common.version";
        /**
         * 邮件发件服务器配置
         */
        String MAIL_PROPERTIES = "sys.common.mail_properties";
    }

    /**
     * 注册相关
     */
    interface Register {

        /**
         * 启用注册邀请码机制
         */
        String ENABLE_REG_CODE = "sys.register.enable_reg_code";
        /**
         * 启用邮件注册机制
         */
        String ENABLE_EMAIL_REG = "sys.register.enable_email_reg";
        /**
         * 注册邀请码
         */
        String SYS_REGISTER_REG_CODE = "sys.register.reg_code";
    }

    /**
     * 安全相关
     */
    interface Safe {

        /**
         * token密钥
         */
        String TOKEN = "sys.safe.token";

        /**
         * 是否允许匿名留言
         */
        String ALLOW_ANONYMOUS_COMMENT = "sys.safe.allow_anonymous_comments";
    }

}
