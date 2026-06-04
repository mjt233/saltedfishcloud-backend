package com.sfc.pxeboot.server.proxydhcp;

import com.sfc.pxeboot.PxeBootProperty;
import com.xiaotao.saltedfishcloud.service.config.ConfigService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.SmartLifecycle;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;

/**
 * ProxyDHCP 服务器
 * 当路由器不支持 PXE 参数配置时，提供 PXE 引导信息
 */
@Slf4j
public class ProxyDhcpServer implements SmartLifecycle {

    /**
     * 日志前缀。
     */
    private static final String LOG_PREFIX = "[PXE-ProxyDHCP]";

    /**
     * DHCP 服务器监听端口。
     */
    private static final int DHCP_PORT = 67;

    /**
     * DHCP 客户端端口。
     */
    private static final int DHCP_CLIENT_PORT = 68;

    /**
     * 报文缓存大小。
     */
    private static final int MAX_PACKET_SIZE = 1500;

    /**
     * BOOTP 固定报文长度（不含 DHCP options）。
     */
    private static final int BOOTP_FIXED_LEN = 236;

    /**
     * DHCP magic cookie。
     */
    private static final int DHCP_MAGIC_COOKIE = 0x63825363;

    /**
     * DHCP option: 消息类型。
     */
    private static final int OPTION_MESSAGE_TYPE = 53;

    /**
     * DHCP option: 服务器标识。
     */
    private static final int OPTION_SERVER_IDENTIFIER = 54;

    /**
     * DHCP option: Vendor Class Identifier。
     */
    private static final int OPTION_VENDOR_CLASS_IDENTIFIER = 60;

    /**
     * DHCP option: TFTP 服务器名。
     */
    private static final int OPTION_TFTP_SERVER_NAME = 66;

    /**
     * DHCP option: 引导文件名。
     */
    private static final int OPTION_BOOTFILE_NAME = 67;

    /**
     * DHCP option: 填充。
     */
    private static final int OPTION_PAD = 0;

    /**
     * DHCP option: 结束。
     */
    private static final int OPTION_END = 255;

    /**
     * DHCP 消息类型：DISCOVER。
     */
    private static final byte DHCP_DISCOVER = 1;

    /**
     * DHCP 消息类型：OFFER。
     */
    private static final byte DHCP_OFFER = 2;

    /**
     * DHCP 消息类型：REQUEST。
     */
    private static final byte DHCP_REQUEST = 3;

    /**
     * DHCP 消息类型：ACK。
     */
    private static final byte DHCP_ACK = 5;

    /**
     * BOOTP 响应操作码。
     */
    private static final byte BOOTREPLY_OP = 2;

    /**
     * 广播标记。
     */
    private static final short FLAG_BROADCAST = (short) 0x8000;

    /**
     * PXE VendorClass 前缀。
     */
    private static final String PXE_VENDOR_PREFIX = "PXEClient";

    /**
     * IPv4 0 地址字节表示。
     */
    private static final byte[] ZERO_IPV4_BYTES = new byte[]{0, 0, 0, 0};

    @Autowired
    private PxeBootProperty property;

    @Autowired
    private ConfigService configService;

    /**
     * 当前 UDP 监听 socket。
     */
    private DatagramSocket socket;

    /**
     * 监听线程。
     */
    private Thread listenerThread;

    /**
     * 当前运行状态。
     */
    private volatile boolean running = false;

    /**
     * 当前下发给客户端的 TFTP 服务器地址。
     */
    private volatile Inet4Address tftpServerAddress;

    /**
     * 当前下发给客户端的引导文件路径。
     */
    private volatile String bootFilePath;

    /**
     * DHCP 请求解析上下文。
     */
    @SuppressWarnings("ClassCanBeRecord")
    private static class DhcpRequestContext {

        /**
         * 事务 ID。
         */
        private final int transactionId;

        /**
         * 硬件类型。
         */
        private final byte hardwareType;

        /**
         * 硬件地址长度。
         */
        private final byte hardwareAddressLength;

        /**
         * BOOTP 标志位。
         */
        private final short flags;

        /**
         * 客户端地址（ciaddr）。
         */
        private final InetAddress clientAddress;

        /**
         * 中继地址（giaddr）。
         */
        private final InetAddress relayAddress;

        /**
         * 客户端硬件地址（chaddr）。
         */
        private final byte[] clientHardwareAddress;

        /**
         * DHCP 消息类型。
         */
        private final byte messageType;

        /**
         * Vendor Class Identifier。
         */
        private final String vendorClassIdentifier;

        /**
         * 构造 DHCP 请求上下文。
         *
         * @param transactionId 事务 ID
         * @param hardwareType 硬件类型
         * @param hardwareAddressLength 硬件地址长度
         * @param flags BOOTP 标志位
         * @param clientAddress 客户端地址
         * @param relayAddress 中继地址
         * @param clientHardwareAddress 客户端硬件地址
         * @param messageType DHCP 消息类型
         * @param vendorClassIdentifier Vendor Class Identifier
         */
        private DhcpRequestContext(int transactionId,
                                   byte hardwareType,
                                   byte hardwareAddressLength,
                                   short flags,
                                   InetAddress clientAddress,
                                   InetAddress relayAddress,
                                   byte[] clientHardwareAddress,
                                   byte messageType,
                                   String vendorClassIdentifier) {
            this.transactionId = transactionId;
            this.hardwareType = hardwareType;
            this.hardwareAddressLength = hardwareAddressLength;
            this.flags = flags;
            this.clientAddress = clientAddress;
            this.relayAddress = relayAddress;
            this.clientHardwareAddress = clientHardwareAddress;
            this.messageType = messageType;
            this.vendorClassIdentifier = vendorClassIdentifier;
        }

        /**
         * 是否要求广播响应。
         *
         * @return true 表示要求广播
         */
        private boolean isBroadcastExpected() {
            return (flags & FLAG_BROADCAST) == FLAG_BROADCAST;
        }
    }

    /**
     * 监听 ProxyDHCP 开关配置变更，动态启停服务
     */
    @PostConstruct
    public void init() {
        configService.addAfterSetListener(PxeBootProperty::getEnableProxyDhcp, newVal -> {
            if (Boolean.TRUE.equals(newVal)) {
                start();
            } else {
                stop();
            }
        });
    }

    @Override
    public synchronized void start() {
        if (running) {
            return;
        }

        if (!Boolean.TRUE.equals(property.getEnableProxyDhcp())) {
            log.info("{} ProxyDHCP 未启用", LOG_PREFIX);
            return;
        }

        String listenAddress = normalizeListenAddress(property.getProxyDhcpListenAddr());
        Inet4Address resolvedTftpAddress = resolveTftpServerAddress(listenAddress);
        if (resolvedTftpAddress == null) {
            log.warn("{} 未找到可用的 TFTP 服务器地址，ProxyDHCP 将以无 TFTP 模式启动", LOG_PREFIX);
        }

        String resolvedBootFilePath = normalizeBootFilePath(property.getIpxeBinaryPath());
        try {
            DatagramSocket newSocket = new DatagramSocket(null);
            newSocket.setReuseAddress(true);
            newSocket.setBroadcast(true);
            newSocket.bind(new InetSocketAddress(listenAddress, DHCP_PORT));

            socket = newSocket;
            tftpServerAddress = resolvedTftpAddress;
            bootFilePath = resolvedBootFilePath;
            running = true;

            listenerThread = new Thread(this::listenLoop, "PXE-ProxyDHCP-Listener");
            listenerThread.setDaemon(true);
            listenerThread.start();

            String tftpAddressDisplay = resolvedTftpAddress == null ? "<未设置>" : resolvedTftpAddress.getHostAddress();
            log.info("{} ProxyDHCP 服务已启动，监听 {}:{}，TFTP={}，bootFile={}",
                LOG_PREFIX, listenAddress, DHCP_PORT, tftpAddressDisplay, resolvedBootFilePath);
        } catch (IOException e) {
            closeSocketQuietly();
            running = false;
            tftpServerAddress = null;
            bootFilePath = null;
            log.error("{} ProxyDHCP 服务启动失败: {}", LOG_PREFIX, e.getMessage(), e);
        }
    }

    @Override
    public synchronized void stop() {
        if (!running && socket == null && listenerThread == null) {
            return;
        }

        running = false;
        closeSocketQuietly();
        if (listenerThread != null) {
            listenerThread.interrupt();
            listenerThread = null;
        }
        tftpServerAddress = null;
        bootFilePath = null;
        log.info("{} ProxyDHCP 服务已停止", LOG_PREFIX);
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        return 0;
    }

    /**
     * 主监听循环。
     */
    private void listenLoop() {
        while (running) {
            try {
                DatagramSocket activeSocket = socket;
                if (activeSocket == null || activeSocket.isClosed()) {
                    return;
                }
                DatagramPacket packet = new DatagramPacket(new byte[MAX_PACKET_SIZE], MAX_PACKET_SIZE);
                activeSocket.receive(packet);
                handlePacket(packet);
            } catch (IOException e) {
                if (running) {
                    log.error("{} 接收 DHCP 报文失败: {}", LOG_PREFIX, e.getMessage(), e);
                }
            }
        }
    }

    /**
     * 处理单个 DHCP 请求报文。
     *
     * @param packet 请求报文
     */
    private void handlePacket(DatagramPacket packet) {
        try {
            DhcpRequestContext request = parseRequest(packet);
            if (request == null || !isTargetPxeRequest(request)) {
                return;
            }

            byte responseType = resolveResponseMessageType(request.messageType);
            byte[] responseData = buildResponsePacket(request, responseType);
            InetSocketAddress targetAddress = resolveResponseAddress(request);

            DatagramSocket activeSocket = socket;
            if (activeSocket == null || activeSocket.isClosed()) {
                return;
            }
            activeSocket.send(new DatagramPacket(responseData, responseData.length, targetAddress));

            log.debug("{} 已响应 PXE 请求: xid={}, vendor={}, type={}, target={}",
                LOG_PREFIX,
                Integer.toUnsignedString(request.transactionId),
                request.vendorClassIdentifier,
                Byte.toUnsignedInt(request.messageType),
                targetAddress);
        } catch (Exception e) {
            log.warn("{} 处理 DHCP 请求失败: {}", LOG_PREFIX, e.getMessage(), e);
        }
    }

    /**
     * 解析 DHCP 请求报文。
     *
     * @param packet 请求报文
     * @return 解析结果，不可识别时返回 null
     */
    private DhcpRequestContext parseRequest(DatagramPacket packet) {
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

            return new DhcpRequestContext(
                transactionId,
                hardwareType,
                hardwareAddressLength,
                flags,
                clientAddress,
                relayAddress,
                clientHardwareAddress,
                messageType,
                vendorClassIdentifier
            );
        } catch (BufferUnderflowException | IOException e) {
            log.debug("{} DHCP 报文解析失败: {}", LOG_PREFIX, e.getMessage());
            return null;
        }
    }

    /**
     * 判断是否为目标 PXE 请求。
     *
     * @param request 请求上下文
     * @return true 表示应当响应
     */
    private boolean isTargetPxeRequest(DhcpRequestContext request) {
        if (request.messageType != DHCP_DISCOVER && request.messageType != DHCP_REQUEST) {
            return false;
        }
        return request.vendorClassIdentifier != null && request.vendorClassIdentifier.startsWith(PXE_VENDOR_PREFIX);
    }

    /**
     * 根据请求消息类型计算响应消息类型。
     *
     * @param requestType 请求消息类型
     * @return 响应消息类型
     */
    private byte resolveResponseMessageType(byte requestType) {
        if (requestType == DHCP_DISCOVER) {
            return DHCP_OFFER;
        }
        return DHCP_ACK;
    }

    /**
     * 构建 DHCP 响应报文。
     *
     * @param request 请求上下文
     * @param responseType 响应消息类型
     * @return DHCP 响应报文字节数组
     */
    private byte[] buildResponsePacket(DhcpRequestContext request, byte responseType) {
        Inet4Address effectiveTftpAddress = tftpServerAddress;
        String effectiveBootFilePath = bootFilePath;
        if (effectiveBootFilePath == null) {
            throw new IllegalStateException("引导文件路径不可为空");
        }

        ByteBuffer buffer = ByteBuffer.allocate(MAX_PACKET_SIZE);
        buffer.put(BOOTREPLY_OP);
        buffer.put(request.hardwareType);
        buffer.put(request.hardwareAddressLength);
        buffer.put((byte) 0);
        buffer.putInt(request.transactionId);
        buffer.putShort((short) 0);
        buffer.putShort(request.flags);
        buffer.putInt(0);
        buffer.putInt(0);
        byte[] tftpAddressBytes = effectiveTftpAddress == null ? ZERO_IPV4_BYTES : effectiveTftpAddress.getAddress();
        buffer.put(tftpAddressBytes);
        buffer.put(request.relayAddress.getAddress());

        byte[] paddedHardwareAddress = new byte[16];
        System.arraycopy(
            request.clientHardwareAddress,
            0,
            paddedHardwareAddress,
            0,
            Math.min(request.clientHardwareAddress.length, paddedHardwareAddress.length)
        );
        buffer.put(paddedHardwareAddress);

        buffer.put(new byte[64]);
        byte[] bootFileFieldBytes = toAsciiBytes(effectiveBootFilePath, 127, "bootfile 字段");
        buffer.put(bootFileFieldBytes);
        buffer.put((byte) 0);
        buffer.put(new byte[128 - bootFileFieldBytes.length - 1]);

        buffer.putInt(DHCP_MAGIC_COOKIE);
        writeOption(buffer, OPTION_MESSAGE_TYPE, new byte[]{responseType});
        writeOption(buffer, OPTION_SERVER_IDENTIFIER, resolveServerIdentifierBytes(effectiveTftpAddress));
        if (effectiveTftpAddress != null) {
            writeOption(buffer, OPTION_TFTP_SERVER_NAME, toAsciiBytes(effectiveTftpAddress.getHostAddress(), 255, "option 66"));
        }
        writeOption(buffer, OPTION_BOOTFILE_NAME, toAsciiBytes(effectiveBootFilePath, 255, "option 67"));
        buffer.put((byte) OPTION_END);

        return Arrays.copyOf(buffer.array(), buffer.position());
    }

    /**
     * 计算响应目标地址。
     *
     * @param request 请求上下文
     * @return 目标 socket 地址
     */
    private InetSocketAddress resolveResponseAddress(DhcpRequestContext request) {
        if (!isZeroAddress(request.relayAddress)) {
            return new InetSocketAddress(request.relayAddress, DHCP_PORT);
        }
        if (request.isBroadcastExpected() || isZeroAddress(request.clientAddress)) {
            return new InetSocketAddress("255.255.255.255", DHCP_CLIENT_PORT);
        }
        return new InetSocketAddress(request.clientAddress, DHCP_CLIENT_PORT);
    }

    /**
     * 写入一个 DHCP option。
     *
     * @param buffer 目标缓冲区
     * @param optionCode option 编码
     * @param value option 数据
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
     * 读取一个 IPv4 地址。
     *
     * @param buffer 数据缓冲区
     * @return IPv4 地址
     * @throws IOException 解析失败
     */
    private InetAddress readIpv4Address(ByteBuffer buffer) throws IOException {
        byte[] bytes = new byte[4];
        buffer.get(bytes);
        return InetAddress.getByAddress(bytes);
    }

    /**
     * 判断地址是否为 0.0.0.0。
     *
     * @param address 待判断地址
     * @return true 表示为 0 地址
     */
    private boolean isZeroAddress(InetAddress address) {
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

    /**
     * 获取 DHCP 服务器标识地址。
     *
     * @param fallbackAddress 回退地址
     * @return 服务器标识地址
     */
    private byte[] resolveServerIdentifierBytes(Inet4Address fallbackAddress) {
        DatagramSocket activeSocket = socket;
        if (activeSocket != null) {
            InetAddress localAddress = activeSocket.getLocalAddress();
            if (localAddress instanceof Inet4Address ipv4 && !ipv4.isAnyLocalAddress()) {
                return ipv4.getAddress();
            }
        }
        if (fallbackAddress != null) {
            return fallbackAddress.getAddress();
        }
        return ZERO_IPV4_BYTES;
    }

    /**
     * 解析 TFTP 服务器地址。
     *
     * @param listenAddress 监听地址
     * @return 可用 IPv4 地址，若无可用地址返回 null
     */
    private Inet4Address resolveTftpServerAddress(String listenAddress) {
        Inet4Address configuredAddress = parseIpv4Address(property.getTftpServerAddr());
        if (configuredAddress != null) {
            return configuredAddress;
        }

        Inet4Address listenIpv4 = parseIpv4Address(listenAddress);
        if (listenIpv4 != null && !listenIpv4.isAnyLocalAddress()) {
            return listenIpv4;
        }

        try {
            for (NetworkInterface networkInterface : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!networkInterface.isUp() || networkInterface.isLoopback() || networkInterface.isVirtual()) {
                    continue;
                }
                for (InetAddress address : Collections.list(networkInterface.getInetAddresses())) {
                    if (address instanceof Inet4Address ipv4
                        && !ipv4.isAnyLocalAddress()
                        && !ipv4.isLoopbackAddress()
                        && !ipv4.isLinkLocalAddress()) {
                        return ipv4;
                    }
                }
            }
        } catch (SocketException e) {
            log.error("{} 解析网卡地址失败: {}", LOG_PREFIX, e.getMessage(), e);
        }
        return null;
    }

    /**
     * 将字符串解析为 IPv4 地址。
     *
     * @param rawAddress 原始地址文本
     * @return IPv4 地址，解析失败返回 null
     */
    private Inet4Address parseIpv4Address(String rawAddress) {
        if (rawAddress == null || rawAddress.isBlank()) {
            return null;
        }
        try {
            InetAddress address = InetAddress.getByName(rawAddress.trim());
            if (address instanceof Inet4Address ipv4) {
                return ipv4;
            }
            log.warn("{} 地址 [{}] 非 IPv4，已忽略", LOG_PREFIX, rawAddress);
            return null;
        } catch (IOException e) {
            log.warn("{} 地址 [{}] 解析失败，已忽略: {}", LOG_PREFIX, rawAddress, e.getMessage());
            return null;
        }
    }

    /**
     * 规范化监听地址。
     *
     * @param listenAddress 监听地址
     * @return 规范化后的监听地址
     */
    private String normalizeListenAddress(String listenAddress) {
        if (listenAddress == null || listenAddress.isBlank()) {
            return "0.0.0.0";
        }
        return listenAddress.trim();
    }

    /**
     * 规范化引导文件路径。
     *
     * @param rawBootFilePath 原始路径
     * @return 规范化后的路径
     */
    private String normalizeBootFilePath(String rawBootFilePath) {
        String normalizedPath = (rawBootFilePath == null || rawBootFilePath.isBlank()) ? "/ipxe.pxe" : rawBootFilePath.trim();
        normalizedPath = normalizedPath.replace('\\', '/');
        if (!normalizedPath.startsWith("/")) {
            normalizedPath = "/" + normalizedPath;
        }
        return normalizedPath;
    }

    /**
     * 将字符串转换为 ASCII 字节数组并按长度截断。
     *
     * @param value 原始字符串
     * @param maxLength 最大长度
     * @param fieldName 字段名
     * @return ASCII 字节数组
     */
    private byte[] toAsciiBytes(String value, int maxLength, String fieldName) {
        byte[] bytes = value.getBytes(StandardCharsets.US_ASCII);
        if (bytes.length <= maxLength) {
            return bytes;
        }
        log.warn("{} {} 长度超过限制，已截断: {} -> {}", LOG_PREFIX, fieldName, bytes.length, maxLength);
        return Arrays.copyOf(bytes, maxLength);
    }

    /**
     * 安静关闭 socket 资源。
     */
    private void closeSocketQuietly() {
        DatagramSocket activeSocket = socket;
        socket = null;
        if (activeSocket != null && !activeSocket.isClosed()) {
            activeSocket.close();
        }
    }
}
