package com.xiaotao.saltedfishcloud.service.config;

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

    /**
     * 常规设置
     */
    interface Common {

        /**
         * FTP配置
         */
        String FTP_PROPERTIES = "sys.common.ftp_properties";
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
    }

}
