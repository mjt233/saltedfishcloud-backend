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
     * Linux 内核 + initrd 配对
     */
    KERNEL_INITRD,

    /**
     * 自定义 iPXE 脚本
     */
    CUSTOM_IPXE_SCRIPT
}
