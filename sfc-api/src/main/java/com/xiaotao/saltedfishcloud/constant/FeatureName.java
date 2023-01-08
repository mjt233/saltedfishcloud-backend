package com.xiaotao.saltedfishcloud.constant;

/**
 * 系统内置的功能特性接口键名
 */
public interface FeatureName {
    /**
     * 压缩/解压缩使用的文件编码
     */
    String ARCHIVE_ENCODING = "archiveEncoding";

    /**
     * 压缩的类型
     */
    String ARCHIVE_TYPE = "archiveType";

    /**
     * 支持解压缩的类型
     */
    String EXTRACT_ARCHIVE_TYPE = "extractArchiveType";

    /**
     * 主背景图配置
     */
    String BG_MAIN = "bgMain";

    /**
     * 断点续传URL
     */
    String BREAKPOINT_URL = "breakpointUrl";

    /**
     * 是否默认使用黑暗模式主题
     */
    String DARK_THEME = "darkTheme";

    /**
     * 是否开启邮箱注册
     */
    String ENABLE_EMAIL_REG = "enableEmailReg";

    /**
     * 是否开启邀请码注册
     */
    String ENABLE_REG_CODE = "enableRegCode";

    /**
     * 系统使用的主存储
     */
    String FILESYSTEM = "fileSystem";

    /**
     * 系统支持缩略图的类型
     */
    String THUMB_TYPE = "thumbType";

    /**
     * 系统版本
     */
    String VERSION = "version";
}
