package com.sfc.pxeboot.server.proxydhcp;

import java.net.InetAddress;

import static com.sfc.pxeboot.server.proxydhcp.DhcpConstants.FLAG_BROADCAST;

/**
 * DHCP 请求解析上下文。
 *
 * @param transactionId          事务 ID
 * @param hardwareType           硬件类型
 * @param hardwareAddressLength  硬件地址长度
 * @param flags                  BOOTP 标志位
 * @param clientAddress          客户端地址（ciaddr）
 * @param relayAddress           中继地址（giaddr）
 * @param clientHardwareAddress  客户端硬件地址（chaddr）
 * @param messageType            DHCP 消息类型
 * @param vendorClassIdentifier  Vendor Class Identifier
 */
public record DhcpRequestContext(
        int transactionId,
        byte hardwareType,
        byte hardwareAddressLength,
        short flags,
        InetAddress clientAddress,
        InetAddress relayAddress,
        byte[] clientHardwareAddress,
        byte messageType,
        String vendorClassIdentifier
) {

    /**
     * 是否要求广播响应。
     *
     * @return true 表示要求广播
     */
    public boolean isBroadcastExpected() {
        return (flags & FLAG_BROADCAST) == FLAG_BROADCAST;
    }
}
