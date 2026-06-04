package com.sfc.pxeboot.model.enums;

/**
 * 启动项类型枚举
 */
public enum BootItemType {

    /**
     * ISO 镜像文件
     */
    ISO,

    /**
     * 目录（包含启动文件）
     */
    DIRECTORY,

    /**
     * Linux 内核 + initrd 配对
     */
    KERNEL_INITRD
}
