package com.sfc.pxeboot.server.proxydhcp;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.net.InetAddress;

import static com.sfc.pxeboot.server.proxydhcp.DhcpConstants.FLAG_BROADCAST;

/**
 * DHCP 请求解析上下文。
 */
@Builder
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class DhcpRequest {
    /** 事务 ID */
    private final int transactionId;
    /** 硬件类型 */
    private final byte hardwareType;
    /** 硬件地址长度 */
    private final byte hardwareAddressLength;
    /** BOOTP 标志位 */
    private final short flags;
    /** 客户端地址（ciaddr） */
    private final InetAddress clientAddress;
    /** 中继地址（giaddr） */
    private final InetAddress relayAddress;
    /** 客户端硬件地址（chaddr） */
    private final byte[] clientHardwareAddress;
    /** DHCP 消息类型 */
    private final byte messageType;
    /** Vendor Class Identifier */
    private final String vendorClassIdentifier;

    /**
     * 是否要求广播响应。
     *
     * @return true 表示要求广播
     */
    public boolean isBroadcastExpected() {
        return (flags & FLAG_BROADCAST) == FLAG_BROADCAST;
    }
}
