package com.sfc.pxeboot.server.tftp;

import com.sfc.pxeboot.PxeBootProperty;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystemManager;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.net.tftp.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.SmartLifecycle;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * PXE TFTP 服务器，负责生命周期管理和 UDP 监听分发。
 */
@Slf4j
public class PxeTftpServer implements SmartLifecycle {

    @Autowired
    private PxeBootProperty property;

    @Autowired
    private TftpFileProvider tftpFileProvider;

    private DatagramSocket socket;
    private Thread listenerThread;
    private volatile boolean running = false;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private TftpReadRequestHandler readRequestHandler;

    @Override
    public void start() {
        if (running) {
            return;
        }
        if (!Boolean.TRUE.equals(property.getEnable())) {
            log.debug("{} PXE 服务未启用，跳过 TFTP 启动", TftpConstants.LOG_PREFIX);
            return;
        }
        try {
            readRequestHandler = new TftpReadRequestHandler(tftpFileProvider);

            socket = new DatagramSocket(new InetSocketAddress(property.getTftpListenAddr(), property.getTftpPort()));
            running = true;
            listenerThread = new Thread(this::listen, "PXE-TFTP-Listener");
            listenerThread.setDaemon(true);
            listenerThread.start();
            log.info("{} TFTP 服务已启动，监听 {}:{}", TftpConstants.LOG_PREFIX, property.getTftpListenAddr(), property.getTftpPort());
        } catch (IOException e) {
            log.error("{} TFTP 服务启动失败: {}", TftpConstants.LOG_PREFIX, e.getMessage(), e);
        }
    }

    @Override
    public void stop() {
        running = false;
        executor.shutdown();
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
        if (listenerThread != null) {
            listenerThread.interrupt();
        }
        log.info("{} TFTP 服务已停止", TftpConstants.LOG_PREFIX);
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
     * UDP 监听循环，接收数据包并分发给处理器。
     */
    private void listen() {
        byte[] buffer = new byte[TftpConstants.MAX_BLOCK_SIZE + 4];
        while (running) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                TFTPPacket tftpPacket = TFTPPacket.newTFTPPacket(packet);
                if (tftpPacket.getType() == TFTPPacket.READ_REQUEST) {
                    executor.submit(() -> readRequestHandler.handle((TFTPReadRequestPacket) tftpPacket, socket));
                }
            } catch (IOException e) {
                if (running) {
                    log.error("{} 接收数据包失败: {}", TftpConstants.LOG_PREFIX, e.getMessage());
                }
            } catch (TFTPPacketException e) {
                log.error("{} 解析 TFTP 数据包失败: {}", TftpConstants.LOG_PREFIX, e.getMessage());
            }
        }
    }
}
