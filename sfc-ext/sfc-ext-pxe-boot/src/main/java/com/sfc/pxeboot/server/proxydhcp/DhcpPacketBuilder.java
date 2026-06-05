package com.sfc.pxeboot.server.proxydhcp;

import lombok.extern.slf4j.Slf4j;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static com.sfc.pxeboot.server.proxydhcp.DhcpConstants.*;

/**
 * DHCP 响应报文构建器。
 */
@Slf4j
public class DhcpPacketBuilder {

    private static final String LOG_PREFIX = "[PXE-ProxyDHCP]";

    private static final int PXE_CLIENT_PORT = 4011;
    private static final int DHCP_CLIENT_PORT = 68;
    private static final int MAX_PACKET_SIZE = 1500;

    /**
     * 构建 DHCP 响应报文。
     *
     * @param request            请求上下文
     * @param responseType       响应消息类型（OFFER 或 ACK）
     * @param tftpServerAddress  TFTP 服务器地址（可为 null）
     * @param bootFilePath       引导文件路径
     * @param serverIdentifier   服务器标识地址字节
     * @return DHCP 响应报文字节数组
     */
    public byte[] buildResponse(DhcpRequestContext request,
                                byte responseType,
                                Inet4Address tftpServerAddress,
                                String bootFilePath,
                                byte[] serverIdentifier) {
        if (bootFilePath == null) {
            throw new IllegalStateException("引导文件路径不可为空");
        }

        ByteBuffer buffer = ByteBuffer.allocate(MAX_PACKET_SIZE);
        buffer.put(BOOTREPLY_OP);
        buffer.put(request.hardwareType());
        buffer.put(request.hardwareAddressLength());
        buffer.put((byte) 0);
        buffer.putInt(request.transactionId());
        buffer.putShort((short) 0);
        buffer.putShort(request.flags());
        buffer.putInt(0);
        buffer.putInt(0);
        byte[] tftpAddressBytes = tftpServerAddress == null ? ZERO_IPV4_BYTES : tftpServerAddress.getAddress();
        buffer.put(tftpAddressBytes);
        buffer.put(request.relayAddress().getAddress());

        byte[] paddedHardwareAddress = new byte[16];
        System.arraycopy(
                request.clientHardwareAddress(),
                0,
                paddedHardwareAddress,
                0,
                Math.min(request.clientHardwareAddress().length, paddedHardwareAddress.length)
        );
        buffer.put(paddedHardwareAddress);

        buffer.put(new byte[64]);
        byte[] bootFileFieldBytes = toAsciiBytes(bootFilePath, 127, "bootfile 字段");
        buffer.put(bootFileFieldBytes);
        buffer.put((byte) 0);
        buffer.put(new byte[128 - bootFileFieldBytes.length - 1]);

        buffer.putInt(DHCP_MAGIC_COOKIE);
        writeOption(buffer, OPTION_MESSAGE_TYPE, new byte[]{responseType});
        writeOption(buffer, OPTION_SERVER_IDENTIFIER, serverIdentifier);
        if (tftpServerAddress != null) {
            writeOption(buffer, OPTION_TFTP_SERVER_NAME, toAsciiBytes(tftpServerAddress.getHostAddress(), 255, "option 66"));
        }
        writeOption(buffer, OPTION_BOOTFILE_NAME, toAsciiBytes(bootFilePath, 255, "option 67"));
        if (request.vendorClassIdentifier() != null && !request.vendorClassIdentifier().isEmpty()) {
            writeOption(buffer, OPTION_VENDOR_CLASS_IDENTIFIER, toAsciiBytes(request.vendorClassIdentifier(), 255, "option 60"));
        }
        buffer.put((byte) OPTION_END);

        return Arrays.copyOf(buffer.array(), buffer.position());
    }

    /**
     * 计算响应目标地址。
     *
     * @param request      请求上下文
     * @param responseType 响应消息类型
     * @return 目标 socket 地址
     */
    public InetSocketAddress resolveResponseAddress(DhcpRequestContext request, byte responseType) {
        int targetPort = responseType == DHCP_OFFER ? DHCP_CLIENT_PORT : PXE_CLIENT_PORT;
        if (!isZeroAddress(request.relayAddress())) {
            return new InetSocketAddress(request.relayAddress(), targetPort);
        }
        if (request.isBroadcastExpected() || isZeroAddress(request.clientAddress())) {
            return new InetSocketAddress("255.255.255.255", targetPort);
        }
        return new InetSocketAddress(request.clientAddress(), targetPort);
    }

    /**
     * 写入一个 DHCP option。
     *
     * @param buffer     目标缓冲区
     * @param optionCode option 编码
     * @param value      option 数据
     */
    private void writeOption(ByteBuffer buffer, int optionCode, byte[] value) {
        int length = Math.min(value.length, 255);
        if (length != value.length) {
            log.warn("{} DHCP option={} 数据长度超过255，已截断", LOG_PREFIX, optionCode);
        }
        buffer.put((byte) optionCode);
        buffer.put((byte) length);
        buffer.put(value, 0, length);
    }

    /**
     * 将字符串转换为 ASCII 字节数组并按长度截断。
     *
     * @param value     原始字符串
     * @param maxLength 最大长度
     * @param fieldName 字段名
     * @return ASCII 字节数组
     */
    private static byte[] toAsciiBytes(String value, int maxLength, String fieldName) {
        byte[] bytes = value.getBytes(StandardCharsets.US_ASCII);
        if (bytes.length <= maxLength) {
            return bytes;
        }
        log.warn("{} {} 长度超过限制，已截断: {} -> {}", LOG_PREFIX, fieldName, bytes.length, maxLength);
        return Arrays.copyOf(bytes, maxLength);
    }

    /**
     * 判断地址是否为 0.0.0.0。
     *
     * @param address 待判断地址
     * @return true 表示为 0 地址
     */
    private static boolean isZeroAddress(InetAddress address) {
        if (address == null) {
            return true;
        }
        byte[] raw = address.getAddress();
        for (byte value : raw) {
            if (value != 0) {
                return false;
            }
        }
        return true;
    }
}
