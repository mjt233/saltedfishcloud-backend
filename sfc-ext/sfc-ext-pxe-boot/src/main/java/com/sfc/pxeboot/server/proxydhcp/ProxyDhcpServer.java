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
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.Collections;

/**
 * ProxyDHCP 服务器
 * 当路由器不支持 PXE 参数配置时，提供 PXE 引导信息
 */
@Slf4j
public class ProxyDhcpServer implements SmartLifecycle {

    private static final String LOG_PREFIX = "[PXE-ProxyDHCP]";
    private static final int DHCP_PORT = 4011;
    private static final int DHCP_MAGIC_COOKIE = 0x63825363;
    private static final byte OPTION_PXE_CLIENT = 60;
    private static final byte OPTION_TFTP_SERVER = 66;
    private static final byte OPTION_BOOTFILE = 67;

    @Autowired
    private PxeBootProperty property;

    @Autowired
    private ConfigService configService;

    private DatagramSocket socket;
    private Thread listenerThread;
    private volatile boolean running = false;

    /**
     * 监听 ProxyDHCP 开关配置变更，动态启停服务
     */
    @PostConstruct
    public void init() {
        configService.addAfterSetListener("pxe-boot.enable-proxydhcp", newVal -> {
            if (Boolean.parseBoolean(newVal)) {
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

        if (!property.getEnableProxyDhcp()) {
            log.info("{} ProxyDHCP 未启用", LOG_PREFIX);
            return;
        }

        try {
            socket = new DatagramSocket(new InetSocketAddress(property.getProxyDhcpListenAddr(), DHCP_PORT));
            running = true;
            listenerThread = new Thread(this::listen, "PXE-ProxyDHCP-Listener");
            listenerThread.setDaemon(true);
            listenerThread.start();
            log.info("{} ProxyDHCP 服务已启动，监听 {}:{}", LOG_PREFIX, property.getProxyDhcpListenAddr(), DHCP_PORT);
        } catch (IOException e) {
            log.error("{} ProxyDHCP 服务启动失败: {}", LOG_PREFIX, e.getMessage(), e);
        }
    }

    @Override
    public synchronized void stop() {
        running = false;
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
        if (listenerThread != null) {
            listenerThread.interrupt();
        }
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

    private void listen() {
        while (running) {
            try {
                byte[] buffer = new byte[1024];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                handlePacket(packet);
            } catch (IOException e) {
                if (running) {
                    log.error("{} 接收数据包失败: {}", LOG_PREFIX, e.getMessage());
                }
            }
        }
    }

    private void handlePacket(DatagramPacket packet) {
        // DHCP 最小包：236 字节固定头 + 4 字节 magic cookie
        if (packet.getLength() < 240) {
            return;
        }

        ByteBuffer buffer = ByteBuffer.wrap(packet.getData(), 0, packet.getLength());

        try {
            // 解析 DHCP 包
            byte op = buffer.get();          // 操作码
            byte htype = buffer.get();       // 硬件类型
            byte hlen = buffer.get();        // 硬件地址长度
            byte hops = buffer.get();        // 跳数
            int xid = buffer.getInt();       // 事务 ID
            short secs = buffer.getShort();  // 秒数
            short flags = buffer.getShort(); // 标志
            int ciaddr = buffer.getInt();    // 客户端 IP
            int yiaddr = buffer.getInt();    // 你的 IP
            int siaddr = buffer.getInt();    // 服务器 IP
            int giaddr = buffer.getInt();    // 网关 IP

            // 仅处理以太网（htype=1, hlen=6）
            if (htype != 1 || hlen != 6) {
                return;
            }

            // 读取客户端 MAC 地址（16 字节）
            byte[] chaddr = new byte[16];
            buffer.get(chaddr);

            // 跳过 server name 和 boot file（共 192 字节）
            buffer.position(buffer.position() + 192);

            // 检查 magic cookie
            int magic = buffer.getInt();
            if (magic != DHCP_MAGIC_COOKIE) {
                return; // 不是 DHCP 包
            }

            // 解析 DHCP 选项
            boolean isPxeClient = false;
            while (buffer.hasRemaining()) {
                byte optionCode = buffer.get();
                if (optionCode == (byte) 0xFF) {
                    break; // 结束选项
                }
                if (optionCode == 0) {
                    continue; // 填充选项
                }

                if (!buffer.hasRemaining()) {
                    break;
                }
                byte optionLen = buffer.get();
                if (optionLen < 0 || buffer.remaining() < (optionLen & 0xFF)) {
                    break; // 选项长度越界
                }
                byte[] optionData = new byte[optionLen & 0xFF];
                buffer.get(optionData);

                if (optionCode == OPTION_PXE_CLIENT) {
                    String vendorClass = new String(optionData);
                    if (vendorClass.contains("PXEClient")) {
                        isPxeClient = true;
                    }
                }
            }

            // 只响应 PXE 客户端
            if (!isPxeClient) {
                return;
            }

            // 发送 ProxyDHCP 响应
            sendProxyDhcpResponse(packet, chaddr, xid);
        } catch (BufferUnderflowException e) {
            log.debug("{} 数据包格式异常，已忽略", LOG_PREFIX);
        }
    }

    private void sendProxyDhcpResponse(DatagramPacket clientPacket, byte[] chaddr, int xid) {
        try {
            String tftpAddr = property.getTftpServerAddr();
            if (tftpAddr == null || tftpAddr.isBlank()) {
                tftpAddr = detectLanIp();
            }
            if (tftpAddr == null || tftpAddr.isBlank()) {
                tftpAddr = property.getProxyDhcpListenAddr();
            }
            if (tftpAddr == null || tftpAddr.isBlank() || isWildcardAddress(tftpAddr)) {
                log.error("{} 未配置有效的 TFTP 服务器地址，请设置 tftp-server-addr，当前 proxydhcp-listen-addr={}", LOG_PREFIX, property.getProxyDhcpListenAddr());
                return;
            }

            byte[] tftpAddrBytes = tftpAddr.getBytes();
            String bootFile = "ipxe.pxe";
            byte[] bootFileBytes = bootFile.getBytes();

            // 构建 DHCP 响应
            ByteBuffer buffer = ByteBuffer.allocate(512);
            buffer.put((byte) 0x02);           // BOOTP reply
            buffer.put((byte) 0x01);           // Ethernet
            buffer.put((byte) 0x06);           // MAC 长度
            buffer.put((byte) 0x00);           // hops
            buffer.putInt(xid);                 // 事务 ID
            buffer.putShort((short) 0);         // secs
            buffer.putShort((short) 0);         // flags
            buffer.putInt(0);                   // ciaddr
            buffer.putInt(0);                   // yiaddr
            buffer.putInt(0);                   // siaddr
            buffer.putInt(0);                   // giaddr
            buffer.put(chaddr);                 // chaddr
            buffer.put(new byte[16 - chaddr.length]); // padding

            // server name (64 bytes)
            byte[] serverName = "SFC-PXE".getBytes();
            buffer.put(serverName);
            buffer.put(new byte[64 - serverName.length]);

            // boot file (128 bytes)
            buffer.put(bootFileBytes);
            buffer.put(new byte[128 - bootFileBytes.length]);

            // magic cookie
            buffer.putInt(DHCP_MAGIC_COOKIE);

            // DHCP 选项
            // Option 53: DHCP Message Type = OFFER
            buffer.put((byte) 53);
            buffer.put((byte) 1);
            buffer.put((byte) 2); // OFFER

            // Option 54: Server Identifier
            buffer.put((byte) 54);
            buffer.put((byte) 4);
            buffer.put(clientPacket.getAddress().getAddress());

            // Option 66: TFTP Server Name
            buffer.put((byte) OPTION_TFTP_SERVER);
            buffer.put((byte) tftpAddrBytes.length);
            buffer.put(tftpAddrBytes);

            // Option 67: Bootfile Name
            buffer.put((byte) OPTION_BOOTFILE);
            buffer.put((byte) bootFileBytes.length);
            buffer.put(bootFileBytes);

            // Option 255: End
            buffer.put((byte) 0xFF);

            // 发送响应
            byte[] data = new byte[buffer.position()];
            buffer.flip();
            buffer.get(data);

            DatagramPacket response = new DatagramPacket(
                data, data.length,
                clientPacket.getAddress(), DHCP_PORT
            );
            socket.send(response);

            log.info("{} 已发送 ProxyDHCP 响应到 {}", LOG_PREFIX, clientPacket.getAddress());
        } catch (IOException e) {
            log.error("{} 发送 ProxyDHCP 响应失败", LOG_PREFIX, e);
        }
    }

    /**
     * 判断地址是否为通配监听地址，通配地址不能直接下发给 PXE 客户端。
     */
    private boolean isWildcardAddress(String address) {
        return "0.0.0.0".equals(address) || "::".equals(address) || "[::]".equals(address);
    }

    /**
     * 自动检测服务器局域网 IPv4 地址
     */
    private String detectLanIp() {
        try {
            for (NetworkInterface iface : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!iface.isUp() || iface.isLoopback()) {
                    continue;
                }
                for (InetAddress addr : Collections.list(iface.getInetAddresses())) {
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        String ip = addr.getHostAddress();
                        log.debug("{} 检测到局域网 IP: {}", LOG_PREFIX, ip);
                        return ip;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("{} 自动检测局域网 IP 失败", LOG_PREFIX, e);
        }
        return null;
    }
}
