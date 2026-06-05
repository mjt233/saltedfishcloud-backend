package com.sfc.pxeboot.server.proxydhcp;

/**
 * DHCP/BOOTP 协议常量定义。
 */
public final class DhcpConstants {

    private DhcpConstants() {
    }

    /**
     * BOOTP 固定报文长度（不含 DHCP options）。
     */
    public static final int BOOTP_FIXED_LEN = 236;

    /**
     * DHCP magic cookie。
     */
    public static final int DHCP_MAGIC_COOKIE = 0x63825363;

    /**
     * DHCP option: 消息类型。
     */
    public static final int OPTION_MESSAGE_TYPE = 53;

    /**
     * DHCP option: 服务器标识。
     */
    public static final int OPTION_SERVER_IDENTIFIER = 54;

    /**
     * DHCP option: Vendor Class Identifier。
     */
    public static final int OPTION_VENDOR_CLASS_IDENTIFIER = 60;

    /**
     * DHCP option: TFTP 服务器名。
     */
    public static final int OPTION_TFTP_SERVER_NAME = 66;

    /**
     * DHCP option: 引导文件名。
     */
    public static final int OPTION_BOOTFILE_NAME = 67;

    /**
     * DHCP option 77: 客户端身份信息
     */
    public static final int OPTION_USER_CLASS_INFO = 77;

    /**
     * DHCP option: 填充。
     */
    public static final int OPTION_PAD = 0;

    /**
     * DHCP option: 结束。
     */
    public static final int OPTION_END = 255;

    /**
     * DHCP 消息类型：DISCOVER。
     */
    public static final byte DHCP_DISCOVER = 1;

    /**
     * DHCP 消息类型：OFFER。
     */
    public static final byte DHCP_OFFER = 2;

    /**
     * DHCP 消息类型：REQUEST。
     */
    public static final byte DHCP_REQUEST = 3;

    /**
     * DHCP 消息类型：ACK。
     */
    public static final byte DHCP_ACK = 5;

    /**
     * BOOTP 响应操作码。
     */
    public static final byte BOOTREPLY_OP = 2;

    /**
     * 广播标记。
     */
    public static final short FLAG_BROADCAST = (short) 0x8000;

    /**
     * PXE VendorClass 前缀。
     */
    public static final String PXE_VENDOR_PREFIX = "PXEClient";

    /**
     * IPv4 0 地址字节表示。
     */
    public static final byte[] ZERO_IPV4_BYTES = new byte[]{0, 0, 0, 0};
}
