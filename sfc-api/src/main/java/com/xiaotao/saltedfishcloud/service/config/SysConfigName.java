package com.xiaotao.saltedfishcloud.service.config;

// @TODO
public interface SysConfigName {
    /**
     * 老配置中的系统版本
     */
    String OLD_VERSION = "VERSION";

    /**
     * 注册邀请码
     */
    String SYS_REGISTER_REG_CODE = "sys.register.regCode";

    /**
     * 存储模式
     */
    String SYS_STORE_TYPE = "sys.store.type";

    /**
     * 自动同步间隔
     */
    String SYNC_INTERVAL = "sys.store.sync_interval";

    /**
     * FTP配置
     */
    String SYS_COMMON_FTP_PROPERTIES = "sys.common.ftp_properties";
    /**
     * 系统版本
     */
    String SYS_COMMON_VERSION = "sys.common.version";

    /**
     * token密钥
     */
    String SYS_COMMON_TOKEN_SECRET = "sys.common.token_secret";

    /**
     * 邮件发件服务器配置
     */
    String SYS_COMMON_MAIL_PROPERTIES = "sys.common.mail_properties";

    /**
     * 启用注册邀请码机制
     */
    String SYS_REGISTER_ENABLE_REG_CODE = "sys.register.enable_reg_code";

    /**
     * 启用邮件注册机制
     */
    String SYS_REGISTER_ENABLE_EMAIL_REG = "sys.register.enable_email_reg";

}
