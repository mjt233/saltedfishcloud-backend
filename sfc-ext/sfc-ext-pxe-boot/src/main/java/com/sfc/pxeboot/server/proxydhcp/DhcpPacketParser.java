package com.sfc.pxeboot.server.proxydhcp;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static com.sfc.pxeboot.server.proxydhcp.DhcpConstants.*;

/**
 * DHCP 报文解析器，负责将原始 {@link DatagramPacket} 解析为 {@link DhcpRequest}。
 */
@Slf4j
public final class DhcpPacketParser {

    private DhcpPacketParser() {
    }

    private static final String LOG_PREFIX = "[PXE-ProxyDHCP]";

    /**
     * 解析 DHCP 请求报文。
     *
     * @param packet 请求报文
     * @return 解析结果，不可识别时返回 null
     */
    public static DhcpRequest parse(DatagramPacket packet) {
        try {
            ByteBuffer buffer = ByteBuffer.wrap(packet.getData(), 0, packet.getLength());
            // BOOTP 固定部分(236字节) + DHCP magic cookie(4字节)
            if (buffer.remaining() < BOOTP_FIXED_LEN + Integer.BYTES) {
                return null;
            }

            // [0] op(1) - 操作码：1=请求
            byte op = buffer.get();
            if (op != 1) {
                return null;
            }
            // [1] htype(1) - 硬件类型；hlen(1) - 硬件地址长度；hops(1) - 跳数
            byte hardwareType = buffer.get();
            byte hardwareAddressLength = buffer.get();
            buffer.get();

            // [4] xid(4) - 事务ID；secs(2) - 秒数；flags(2) - 标志位
            int transactionId = buffer.getInt();
            buffer.getShort();
            short flags = buffer.getShort();

            // [12] ciaddr(4) - 客户端IP；yiaddr(4) - 分配IP；siaddr(4) - 服务器IP；giaddr(4) - 中继IP
            InetAddress clientAddress = readIpv4Address(buffer);
            readIpv4Address(buffer);
            readIpv4Address(buffer);
            InetAddress relayAddress = readIpv4Address(buffer);

            // [28] chaddr(16) - 客户端硬件地址；sname(64) - 服务器名；file(128) - 引导文件名
            byte[] clientHardwareAddress = new byte[16];
            buffer.get(clientHardwareAddress);
            buffer.position(buffer.position() + 64 + 128);

            // [236] magic cookie(4) - DHCP 魔术字 0x63825363
            int magicCookie = buffer.getInt();
            if (magicCookie != DHCP_MAGIC_COOKIE) {
                return null;
            }

            // [240+] DHCP options: code(1) + length(1) + value(length)
            Map<Integer, byte[]> options = new HashMap<>();
            while (buffer.hasRemaining()) {
                int optionCode = Byte.toUnsignedInt(buffer.get());
                if (optionCode == OPTION_PAD) {
                    continue;
                }
                if (optionCode == OPTION_END) {
                    break;
                }
                if (!buffer.hasRemaining()) {
                    break;
                }
                int optionLength = Byte.toUnsignedInt(buffer.get());
                if (buffer.remaining() < optionLength) {
                    break;
                }
                byte[] optionValue = new byte[optionLength];
                buffer.get(optionValue);
                options.put(optionCode, optionValue);
            }

            byte messageType = 0;
            byte[] mtOpt = options.get(OPTION_MESSAGE_TYPE);
            if (mtOpt != null && mtOpt.length > 0) {
                messageType = mtOpt[0];
            }
            String vendorClassIdentifier = "";
            byte[] vciOpt = options.get(OPTION_VENDOR_CLASS_IDENTIFIER);
            if (vciOpt != null) {
                vendorClassIdentifier = new String(vciOpt, StandardCharsets.US_ASCII).trim();
            }

            return DhcpRequest.builder()
                    .transactionId(transactionId)
                    .hardwareType(hardwareType)
                    .hardwareAddressLength(hardwareAddressLength)
                    .flags(flags)
                    .clientAddress(clientAddress)
                    .relayAddress(relayAddress)
                    .clientHardwareAddress(clientHardwareAddress)
                    .messageType(messageType)
                    .vendorClassIdentifier(vendorClassIdentifier)
                    .options(Collections.unmodifiableMap(options))
                    .build();
        } catch (BufferUnderflowException | IOException e) {
            log.debug("{} DHCP 报文解析失败: {}", LOG_PREFIX, e.getMessage());
            return null;
        }
    }

    /**
     * 读取一个 IPv4 地址。
     *
     * @param buffer 数据缓冲区
     * @return IPv4 地址
     * @throws IOException 解析失败
     */
    private static InetAddress readIpv4Address(ByteBuffer buffer) throws IOException {
        byte[] bytes = new byte[4];
        buffer.get(bytes);
        return InetAddress.getByAddress(bytes);
    }
}
