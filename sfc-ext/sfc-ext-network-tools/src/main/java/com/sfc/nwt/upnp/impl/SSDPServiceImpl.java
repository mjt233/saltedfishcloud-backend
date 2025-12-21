package com.sfc.nwt.upnp.impl;

import com.sfc.nwt.upnp.*;
import com.sfc.nwt.upnp.constants.UpnpConstants;
import com.sfc.nwt.upnp.event.NotifySSDPEvent;
import com.sfc.nwt.upnp.event.SSDPEvent;
import com.sfc.nwt.upnp.event.SSDPEventListener;
import com.sfc.nwt.upnp.model.SsdpMessage;
import com.sfc.nwt.upnp.model.NotifySsdpMessage;
import com.sfc.nwt.utils.NetworkUtils;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.context.SmartLifecycle;

import java.io.IOException;
import java.net.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.sfc.nwt.upnp.constants.UpnpConstants.Network.MULTICAST_GROUP;
import static com.sfc.nwt.upnp.constants.UpnpConstants.Network.MULTICAST_PORT;

@Slf4j
public class SSDPServiceImpl implements SSDPService, SmartLifecycle {
    private final static String LOG_PREFIX = "[SSDP服务发现]";
    private MulticastSocket ms;
    private List<NetworkInterface> nis;
    private ScheduledExecutorService executorService;

    private final List<SSDPEventListener> listeners = new ArrayList<>();

    @Override
    public void addEventListener(SSDPEventListener listener) {
        listeners.add(listener);
    }

    @Override
    public void removeEventListener(SSDPEventListener listener) {
        listeners.remove(listener);
    }

    /**
     * 刷新系统的网络接口列表
     */
    private void refreshNetworkInterfaces() throws SocketException {
        nis = NetworkUtils.getAllConnectedInterface()
                .stream()
                .filter(ni -> {
                    try {
                        return ni.isUp() && ni.supportsMulticast() && ni.inetAddresses().anyMatch(addr -> addr.getHostAddress().contains("."));
                    } catch (SocketException e) {
                        log.error("{} 检查接口 {} 是否支持组播异常", LOG_PREFIX, ni.getName(), e);
                        return false;
                    }
                })
                .toList();
    }

    @Override
    public void stop() {
        if (ms == null) {
            return;
        }
        log.debug("{} 停止订阅 SSDP 组播消息", LOG_PREFIX);

        // 停止定时发送 ssdp
        executorService.shutdown();
        executorService = null;

        // 离开组播组订阅
        leaveMulticastGroup();

        // 关闭 socket
        ms.close();
        ms = null;
    }

    @Override
    public void doSearch(String st) throws IOException {
        if (ms == null) {
            return;
        }

        InetSocketAddress groupAddr = new InetSocketAddress(InetAddress.getByName(MULTICAST_GROUP), MULTICAST_PORT);
        byte[] reqBuf = UpnpUtils.buildSSDPSearchMessage(st).getBytes();
        DatagramPacket packet = new DatagramPacket(reqBuf, 0, reqBuf.length, groupAddr);
        ms.send(packet);
        log.debug("发送 SSDP，搜索类型：{}", st);
    }

    protected void joinMulticastGroup() throws IOException {
        InetSocketAddress groupAddr = new InetSocketAddress(InetAddress.getByName(MULTICAST_GROUP), MULTICAST_PORT);
        this.refreshNetworkInterfaces();

        // 在所有接口上订阅组播消息
        for (NetworkInterface ni : nis) {
            log.info("{} 在接口 {} 上加入 SSDP 消息组播。接口地址: {}", LOG_PREFIX, ni.getName(), ni.getInterfaceAddresses());
            ms.joinGroup(groupAddr, ni);
        }
    }

    protected void leaveMulticastGroup() {
        try {
            InetAddress group = InetAddress.getByName(MULTICAST_GROUP);
            InetSocketAddress address = new InetSocketAddress(group, MULTICAST_PORT);

            for (NetworkInterface ni : nis) {
                ms.leaveGroup(address, ni);
            }
        } catch (IOException e) {
            log.error("{} 离开 SSDP 消息组播组失败", LOG_PREFIX, e);
        }
    }


    @Override
    public void start() {
        stop();
        log.debug("{} 开始订阅 SSDP 组播消息", LOG_PREFIX);
        Thread thread = new Thread(() -> {
            try {
                this.ms = new MulticastSocket(MULTICAST_PORT);
                ms.setReuseAddress(true);

                // 加入组播订阅
                this.joinMulticastGroup();

                // 发送搜索组播消息
                this.executorService = Executors.newScheduledThreadPool(1);
                executorService.scheduleAtFixedRate(() -> {
                    try {
                        doSearch(UpnpConstants.SsdpType.ALL);
                    } catch (IOException e) {
                        log.error("发送 SSDP 消息出错: ", e);
                    }
                }, 0, 30, TimeUnit.SECONDS);

                byte[] receiveBuffer = new byte[4096];
                DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
                while (true) {
                    // 接收组播消息
                    ms.receive(receivePacket);
                    InetAddress address = receivePacket.getAddress();
                    log.debug("收到来自 {}/{} 的 SSDP 消息", address.getHostAddress(), address.getHostName());

                    // 构造事件，分发到监听器
                    SSDPEvent<? extends SsdpMessage> event = buildSSDPEvent(receivePacket, address);

                    // 分发事件
                    this.dispatchEvent(event);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        thread.start();
    }

    private void dispatchEvent(SSDPEvent<? extends SsdpMessage> event) {
        for (SSDPEventListener listener : listeners) {
            try {
                listener.handleSSDPEvent(event);
            } catch (Throwable e) {
                log.error("处理 SSDP 事件回调出错: ", e);
            }
        }
    }

    /**
     * 构造 SSDP 响应事件
     * @param receivePacket 收到的 SSDP 响应数据包
     * @param address   来源地址
     */
    @NotNull
    private static SSDPEvent<? extends SsdpMessage> buildSSDPEvent(DatagramPacket receivePacket, InetAddress address) {
        // 构造 SSDP 报文字符串
        String response = new String(receivePacket.getData(), 0, receivePacket.getLength());

        // 解析 SSDP 报文
        SSDPEvent<? extends SsdpMessage> event;
        if (response.startsWith("NOTIFY")) {
            NotifySsdpMessage notifySsdpMessage = new NotifySsdpMessage(response);
            event = new NotifySSDPEvent(response, notifySsdpMessage, address);
        } else {
            SsdpMessage message = new SsdpMessage(response);
            event = new SSDPEvent<>(response, message, address);
        }
        return event;
    }

    @Override
    public boolean isRunning() {
        return ms != null;
    }
}
