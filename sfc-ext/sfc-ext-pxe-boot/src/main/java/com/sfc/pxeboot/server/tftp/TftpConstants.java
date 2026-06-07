package com.sfc.pxeboot.server.tftp;

/**
 * TFTP 协议相关常量定义。
 */
public final class TftpConstants {

    private TftpConstants() {
    }

    /**
     * 日志前缀。
     */
    public static final String LOG_PREFIX = "[PXE-TFTP]";

    /**
     * TFTP 默认块大小（RFC 1350）。
     */
    public static final int DEFAULT_BLOCK_SIZE = 512;

    /**
     * TFTP 最大块大小。
     */
    public static final int MAX_BLOCK_SIZE = 65464;

    /**
     * OACK 操作码。
     */
    public static final int OACK_OPCODE = 6;

    /**
     * TFTP 服务固定的资源路径
     */
    public static final class ResourcePath {
        /**
         * iPXE固件本体（Legacy BIOS 版本）的 TFTP 资源路径
         */
        public static final String I_PXE = "ipxe.pxe";

        /**
         * iPXE UEFI 固件的 TFTP 资源路径
         */
        public static final String I_PXE_UEFI = "ipxe-x86_64.efi";

        /**
         * 适用于 iPXE 的菜单脚本 TFTP 资源路径
         */
        public static final String I_PXE_MENU = "menu.ipxe";
    }

}
