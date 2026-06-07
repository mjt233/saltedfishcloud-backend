package com.sfc.pxeboot.model.enums;

/**
 * ISO 启动方式枚举
 */
public enum IsoBootMethod {

    /**
     * 使用 memdisk 将整个 ISO 加载到内存启动
     */
    MEMDISK,

    /**
     * 从 ISO 中提取 kernel 和 initrd 启动（适用于 Linux ISO）
     */
    KERNEL,

    /**
     * 使用 wimboot 启动（适用于 Windows PE/WIM）
     */
    WIMBOOT,

    /**
     * 使用 sanboot 启动
     */
    SANBOOT,

    /**
     * 自定义编写 ISO 的 iPXE 启动脚本
     */
    CUSTOM_IPXE_SCRIPT
}
