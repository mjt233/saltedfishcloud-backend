package com.sfc.pxeboot.server.proxydhcp;

import com.sfc.pxeboot.PxeBootProperty;
import com.sfc.pxeboot.server.tftp.TftpConstants;
import com.xiaotao.saltedfishcloud.helper.PathBuilder;
import com.xiaotao.saltedfishcloud.service.config.ConfigService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.SmartLifecycle;

import java.io.IOException;
import java.net.*;
import java.nio.channels.DatagramChannel;
import java.util.Arrays;
import java.util.Collections;

import static com.sfc.pxeboot.server.proxydhcp.DhcpConstants.OPTION_CLIENT_ARCHITECTURE;
import static com.sfc.pxeboot.server.proxydhcp.DhcpConstants.OPTION_USER_CLASS_INFO;

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
     * PXE ProxyDHCP 监听端口及响应目标端口。
     */
    private static final int PXE_CLIENT_PORT = 4011;

    /**
     * 报文缓存大小。
     */
    private static final int MAX_PACKET_SIZE = 1500;

    @Autowired
    private PxeBootProperty property;

    @Autowired
    private ConfigService configService;

    /**
     * DHCP 响应报文构建器。
     */
    private final DhcpPacketBuilder builder = new DhcpPacketBuilder();

    /**
     * DHCP DatagramChannel（端口 67），用于接收 DISCOVER。
     */
    private DatagramChannel dhcpChannel;

    /**
     * ProxyDHCP DatagramChannel（端口 4011），用于接收 REQUEST。
     */
    private DatagramChannel proxyChannel;

    /**
     * DHCP 监听 socket（端口 67），用于接收 DISCOVER。
     */
    private DatagramSocket dhcpSocket;

    /**
     * ProxyDHCP 监听 socket（端口 4011），用于接收 REQUEST。
     */
    private DatagramSocket proxySocket;

    /**
     * DHCP 监听线程（端口 67）。
     */
    private Thread dhcpListenerThread;

    /**
     * ProxyDHCP 监听线程（端口 4011）。
     */
    private Thread proxyListenerThread;

    /**
     * 当前运行状态。
     */
    private volatile boolean running = false;

    /**
     * 当前下发给客户端的 TFTP 服务器地址。
     */
    private volatile Inet4Address tftpServerAddress;

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

        try {
            DatagramChannel newDhcpChannel = DatagramChannel.open(StandardProtocolFamily.INET);
            DatagramSocket newDhcpSocket = newDhcpChannel.socket();
            newDhcpSocket.setReuseAddress(true);
            newDhcpSocket.setBroadcast(true);
            newDhcpSocket.bind(new InetSocketAddress(listenAddress, DHCP_PORT));

            DatagramChannel newProxyChannel = DatagramChannel.open(StandardProtocolFamily.INET);
            DatagramSocket newProxySocket = newProxyChannel.socket();
            newProxySocket.setReuseAddress(true);
            newProxySocket.setBroadcast(true);
            newProxySocket.bind(new InetSocketAddress(listenAddress, PXE_CLIENT_PORT));

            dhcpChannel = newDhcpChannel;
            proxyChannel = newProxyChannel;
            dhcpSocket = newDhcpSocket;
            proxySocket = newProxySocket;
            tftpServerAddress = resolvedTftpAddress;
            running = true;

            dhcpListenerThread = new Thread(this::dhcpListenLoop, "PXE-DHCP-Listener");
            dhcpListenerThread.setDaemon(true);
            dhcpListenerThread.start();

            proxyListenerThread = new Thread(this::proxyListenLoop, "PXE-ProxyDHCP-Listener");
            proxyListenerThread.setDaemon(true);
            proxyListenerThread.start();

            String tftpAddressDisplay = resolvedTftpAddress == null ? "<未设置>" : resolvedTftpAddress.getHostAddress();
            log.info("{} ProxyDHCP 服务已启动，DHCP监听 {}:{}，Proxy监听 {}:{}，TFTP={}",
                LOG_PREFIX, listenAddress, DHCP_PORT, listenAddress, PXE_CLIENT_PORT, tftpAddressDisplay);
        } catch (IOException e) {
            closeAllSocketsQuietly();
            running = false;
            tftpServerAddress = null;
            log.error("{} ProxyDHCP 服务启动失败: {}", LOG_PREFIX, e.getMessage(), e);
        }
    }

    @Override
    public synchronized void stop() {
        if (!running && dhcpSocket == null && proxySocket == null
            && dhcpListenerThread == null && proxyListenerThread == null) {
            return;
        }

        running = false;
        closeAllSocketsQuietly();
        if (dhcpListenerThread != null) {
            dhcpListenerThread.interrupt();
            dhcpListenerThread = null;
        }
        if (proxyListenerThread != null) {
            proxyListenerThread.interrupt();
            proxyListenerThread = null;
        }
        tftpServerAddress = null;
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
     * DHCP 监听循环（端口 67），接收 DISCOVER 请求。
     */
    private void dhcpListenLoop() {
        listenOnSocket(dhcpSocket, "DHCP");
    }

    /**
     * ProxyDHCP 监听循环（端口 4011），接收 REQUEST 请求。
     */
    private void proxyListenLoop() {
        listenOnSocket(proxySocket, "ProxyDHCP");
    }

    /**
     * 通用 socket 监听循环。
     *
     * @param activeSocket 监听的 socket
     * @param socketName   socket 名称（用于日志）
     */
    private void listenOnSocket(DatagramSocket activeSocket, String socketName) {
        while (running) {
            try {
                if (activeSocket == null || activeSocket.isClosed()) {
                    return;
                }
                DatagramPacket packet = new DatagramPacket(new byte[MAX_PACKET_SIZE], MAX_PACKET_SIZE);
                activeSocket.receive(packet);
                handlePacket(packet, activeSocket);
            } catch (IOException e) {
                if (running) {
                    log.error("{} 接收报文失败({}): {}", LOG_PREFIX, socketName, e.getMessage(), e);
                }
            }
        }
    }

    /**
     * 处理单个 DHCP 请求报文。
     *
     * @param packet       请求报文
     * @param sourceSocket 接收到报文的 socket
     */
    private void handlePacket(DatagramPacket packet, DatagramSocket sourceSocket) {
        try {
            DhcpRequest request = DhcpPacketParser.parse(packet);
            if (request == null || !isTargetPxeRequest(request)) {
                return;
            }

            byte[] userClassInfo = request.getOption(OPTION_USER_CLASS_INFO);
            String bootFilePath;
            if (userClassInfo != null && Arrays.equals(userClassInfo, "iPXE".getBytes())) {
                // 针对 iPXE 客户端，响应 iPXE 菜单脚本
                bootFilePath = TftpConstants.ResourcePath.I_PXE_MENU;
            } else if (isUefiClient(request)) {
                // UEFI PXE 客户端，响应 UEFI 版本的 iPXE 固件
                bootFilePath = TftpConstants.ResourcePath.I_PXE_UEFI;
            } else {
                // Legacy BIOS PXE 客户端，响应 Legacy 版本的 iPXE 固件
                bootFilePath = TftpConstants.ResourcePath.I_PXE;
            }

            byte responseType = request.getMessageType() == DhcpConstants.DHCP_DISCOVER
                    ? DhcpConstants.DHCP_OFFER
                    : DhcpConstants.DHCP_ACK;
            byte[] serverIdentifier = resolveServerIdentifierBytes();
            byte[] responseData = builder.buildResponse(request, responseType, tftpServerAddress, bootFilePath, serverIdentifier);
            InetSocketAddress targetAddress = builder.resolveResponseAddress(request, responseType);

            DatagramSocket sendSocket;
            if (responseType == DhcpConstants.DHCP_ACK && proxySocket != null && !proxySocket.isClosed()) {
                sendSocket = proxySocket;
            } else {
                sendSocket = dhcpSocket != null ? dhcpSocket : sourceSocket;
            }
            if (sendSocket != null && !sendSocket.isClosed()) {
                sendSocket.send(new DatagramPacket(responseData, responseData.length, targetAddress));
            }

            log.debug("{} 已响应 DHCP 请求: responseType={}, target={} bootFilePath={}",
                    LOG_PREFIX,
                    responseType == DhcpConstants.DHCP_OFFER ? "OFFER" : "ACK",
                    targetAddress,
                    bootFilePath);
        } catch (Exception e) {
            log.warn("{} 处理 DHCP 请求失败: {}", LOG_PREFIX, e.getMessage(), e);
        }
    }

    /**
     * 判断是否为目标 PXE 请求。
     *
     * @param request 请求上下文
     * @return true 表示应当响应
     */
    private boolean isTargetPxeRequest(DhcpRequest request) {
        if (request.getMessageType() != DhcpConstants.DHCP_DISCOVER && request.getMessageType() != DhcpConstants.DHCP_REQUEST) {
            return false;
        }
        if (request.getMessageType() == DhcpConstants.DHCP_REQUEST) {
            byte[] reqServerId = request.getOption(DhcpConstants.OPTION_SERVER_IDENTIFIER);
            if (reqServerId != null && reqServerId.length > 0) {
                byte[] myServerId = resolveServerIdentifierBytes();
                if (!Arrays.equals(reqServerId, myServerId)) {
                    log.debug("{} 忽略非目标 REQUEST 报文", LOG_PREFIX);
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * 判断 PXE 客户端是否为 UEFI 架构。
     * 通过 DHCP option 93（Client System Architecture Type）进行判断。
     *
     * @param request DHCP 请求
     * @return true 表示为 UEFI 客户端
     */
    private boolean isUefiClient(DhcpRequest request) {
        byte[] archType = request.getOption(OPTION_CLIENT_ARCHITECTURE);
        if (archType == null || archType.length < 2) {
            return false;
        }
        // option 93 为 2 字节大端序，值为 0 表示 Legacy BIOS，非 0 表示 UEFI
        short value = (short) ((archType[0] & 0xFF) << 8 | (archType[1] & 0xFF));
        return value != DhcpConstants.ARCH_TYPE_X86_BIOS;
    }

    /**
     * 获取 DHCP 服务器标识地址。
     *
     * @return 服务器标识地址字节
     */
    private byte[] resolveServerIdentifierBytes() {
        DatagramSocket activeSocket = dhcpSocket != null ? dhcpSocket : proxySocket;
        if (activeSocket != null) {
            InetAddress localAddress = activeSocket.getLocalAddress();
            if (localAddress instanceof Inet4Address ipv4 && !ipv4.isAnyLocalAddress()) {
                return ipv4.getAddress();
            }
        }
        if (tftpServerAddress != null) {
            return tftpServerAddress.getAddress();
        }
        return DhcpConstants.ZERO_IPV4_BYTES;
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
        if (rawBootFilePath == null || rawBootFilePath.isBlank()) {
            return "/ipxe.pxe";
        } else {
            PathBuilder pathBuilder = new PathBuilder();
            pathBuilder.setForcePrefix(true);
            pathBuilder.append(rawBootFilePath.trim());
            return pathBuilder.toString();
        }
    }

    /**
     * 安静关闭所有 socket 资源。
     */
    private void closeAllSocketsQuietly() {
        closeSocketQuietly(dhcpSocket);
        dhcpSocket = null;
        dhcpChannel = null;
        closeSocketQuietly(proxySocket);
        proxySocket = null;
        proxyChannel = null;
    }

    /**
     * 安静关闭单个 socket 资源。
     *
     * @param socketToClose 待关闭的 socket
     */
    private void closeSocketQuietly(DatagramSocket socketToClose) {
        if (socketToClose != null && !socketToClose.isClosed()) {
            socketToClose.close();
        }
    }
}
