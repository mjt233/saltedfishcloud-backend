package com.sfc.pxeboot.server.proxydhcp;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import static com.sfc.pxeboot.server.proxydhcp.DhcpConstants.*;

/**
 * DHCP 报文解析器，负责将原始 {@link DatagramPacket} 解析为 {@link DhcpRequest}。
 */
@Slf4j
public class DhcpPacketParser {

    private static final String LOG_PREFIX = "[PXE-ProxyDHCP]";

    /**
     * 解析 DHCP 请求报文。
     *
     * @param packet 请求报文
     * @return 解析结果，不可识别时返回 null
     */
    public DhcpRequest parse(DatagramPacket packet) {
        try {
            ByteBuffer buffer = ByteBuffer.wrap(packet.getData(), 0, packet.getLength());
            if (buffer.remaining() < BOOTP_FIXED_LEN + Integer.BYTES) {
                return null;
            }

            byte op = buffer.get();
            if (op != 1) {
                return null;
            }
            byte hardwareType = buffer.get();
            byte hardwareAddressLength = buffer.get();
            buffer.get();

            int transactionId = buffer.getInt();
            buffer.getShort();
            short flags = buffer.getShort();

            InetAddress clientAddress = readIpv4Address(buffer);
            readIpv4Address(buffer);
            readIpv4Address(buffer);
            InetAddress relayAddress = readIpv4Address(buffer);

            byte[] clientHardwareAddress = new byte[16];
            buffer.get(clientHardwareAddress);
            buffer.position(buffer.position() + 64 + 128);

            int magicCookie = buffer.getInt();
            if (magicCookie != DHCP_MAGIC_COOKIE) {
                return null;
            }

            byte messageType = 0;
            String vendorClassIdentifier = "";
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
                if (optionCode == OPTION_MESSAGE_TYPE && optionLength > 0) {
                    messageType = optionValue[0];
                } else if (optionCode == OPTION_VENDOR_CLASS_IDENTIFIER) {
                    vendorClassIdentifier = new String(optionValue, StandardCharsets.US_ASCII).trim();
                }
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
