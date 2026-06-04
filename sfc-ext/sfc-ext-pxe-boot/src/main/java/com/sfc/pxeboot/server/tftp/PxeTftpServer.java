package com.sfc.pxeboot.server.tftp;

import com.sfc.pxeboot.PxeBootProperty;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystemManager;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.net.tftp.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.SmartLifecycle;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * PXE TFTP 服务器
 * 使用 Apache Commons Net 的 TFTP 类实现
 */
@Slf4j
public class PxeTftpServer implements SmartLifecycle {

    private static final String LOG_PREFIX = "[PXE-TFTP]";
    private static final int BLOCK_SIZE = 512;

    @Autowired
    private PxeBootProperty property;

    @Autowired
    private DiskFileSystemManager diskFileSystemManager;

    private DatagramSocket socket;
    private Thread listenerThread;
    private volatile boolean running = false;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    @Override
    public void start() {
        if (running) {
            return;
        }
        if (!Boolean.TRUE.equals(property.getEnable())) {
            log.debug("{} PXE 服务未启用，跳过 TFTP 启动", LOG_PREFIX);
            return;
        }
        try {
            socket = new DatagramSocket(new InetSocketAddress(property.getTftpListenAddr(), property.getTftpPort()));
            running = true;
            listenerThread = new Thread(this::listen, "PXE-TFTP-Listener");
            listenerThread.setDaemon(true);
            listenerThread.start();
            log.info("{} TFTP 服务已启动，监听 {}:{}", LOG_PREFIX, property.getTftpListenAddr(), property.getTftpPort());
        } catch (IOException e) {
            log.error("{} TFTP 服务启动失败: {}", LOG_PREFIX, e.getMessage(), e);
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
        log.info("{} TFTP 服务已停止", LOG_PREFIX);
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
        byte[] buffer = new byte[BLOCK_SIZE + 4];
        while (running) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                // 解析 TFTP 数据包
                TFTPPacket tftpPacket = TFTPPacket.newTFTPPacket(packet);
                if (tftpPacket.getType() == TFTPPacket.READ_REQUEST) {
                    executor.submit(() -> handleReadRequest((TFTPReadRequestPacket) tftpPacket));
                }
            } catch (IOException e) {
                if (running) {
                    log.error("{} 接收数据包失败: {}", LOG_PREFIX, e.getMessage());
                }
            } catch (TFTPPacketException e) {
                log.error("{} 解析 TFTP 数据包失败: {}", LOG_PREFIX, e.getMessage());
            }
        }
    }

    private void handleReadRequest(TFTPReadRequestPacket request) {
        String filename = request.getFilename();
        log.info("{} 收到读请求: {} 来自 {}", LOG_PREFIX, filename, request.getAddress());

        try (InputStream fileStream = openFileStream(filename)) {
            if (fileStream == null) {
                sendError(request, "文件不存在: " + filename);
                return;
            }

            try (DatagramSocket dataSocket = new DatagramSocket()) {
                dataSocket.setSoTimeout(5000);

                byte[] buffer = new byte[BLOCK_SIZE];
                int bytesRead;
                int blockNumber = 1;

                while ((bytesRead = fileStream.read(buffer)) != -1) {
                    byte[] data = Arrays.copyOf(buffer, bytesRead);

                    TFTPDataPacket dataPacket = new TFTPDataPacket(
                        request.getAddress(), request.getPort(), blockNumber, data);
                    sendPacket(dataSocket, dataPacket);

                    if (bytesRead < BLOCK_SIZE) {
                        break;
                    }

                    TFTPPacket ack = receiveAck(dataSocket, blockNumber);
                    if (ack == null) {
                        log.warn("{} 未收到 ACK (块号: {}), 传输中断", LOG_PREFIX, blockNumber);
                        return;
                    }
                    blockNumber++;
                }

                log.info("{} 文件传输完成: {} ({} 块)", LOG_PREFIX, filename, blockNumber);
            }
        } catch (Exception e) {
            log.error("{} 文件传输失败: {}", LOG_PREFIX, filename, e);
        }
    }

    /**
     * 打开网盘文件输入流，用于流式传输
     */
    private InputStream openFileStream(String filename) {
        try {
            String normalizedPath = filename.replace("\\", "/");
            if (normalizedPath.startsWith("/")) {
                normalizedPath = normalizedPath.substring(1);
            }

            int lastSlash = normalizedPath.lastIndexOf('/');
            String dirPath;
            String fileName;
            if (lastSlash >= 0) {
                dirPath = "/" + normalizedPath.substring(0, lastSlash);
                fileName = normalizedPath.substring(lastSlash + 1);
            } else {
                dirPath = "/";
                fileName = normalizedPath;
            }

            Resource resource = diskFileSystemManager.getMainFileSystem().getResource(0L, dirPath, fileName);
            if (resource == null) {
                return null;
            }

            return resource.getInputStream();
        } catch (Exception e) {
            log.error("{} 打开文件流失败: {}", LOG_PREFIX, filename, e);
            return null;
        }
    }

    /**
     * 发送 TFTP 数据包
     */
    private void sendPacket(DatagramSocket socket, TFTPDataPacket packet) throws IOException {
        DatagramPacket datagram = packet.newDatagram();
        socket.send(datagram);
    }

    /**
     * 接收 TFTP ACK 确认包
     */
    private TFTPPacket receiveAck(DatagramSocket socket, int expectedBlock) throws IOException {
        try {
            byte[] buf = new byte[BLOCK_SIZE + 4];
            DatagramPacket packet = new DatagramPacket(buf, buf.length);
            socket.receive(packet);
            TFTPPacket tftpPacket = TFTPPacket.newTFTPPacket(packet);
            if (tftpPacket.getType() == TFTPPacket.ACKNOWLEDGEMENT) {
                TFTPAckPacket ack = (TFTPAckPacket) tftpPacket;
                if (ack.getBlockNumber() != expectedBlock) {
                    log.error("{} ACK 块号不匹配: 期望 {}, 收到 {}", LOG_PREFIX, expectedBlock, ack.getBlockNumber());
                    return null;
                }
                return ack;
            }
            return null;
        } catch (SocketTimeoutException e) {
            log.warn("{} 等待 ACK 超时 (块号: {})", LOG_PREFIX, expectedBlock);
            return null;
        } catch (TFTPPacketException e) {
            log.error("{} 解析 ACK 包失败 (块号: {}): {}", LOG_PREFIX, expectedBlock, e.getMessage());
            return null;
        }
    }

    private void sendError(TFTPReadRequestPacket request, String message) {
        try {
            TFTPErrorPacket errorPacket = new TFTPErrorPacket(
                request.getAddress(),
                request.getPort(),
                    TFTPErrorPacket.FILE_NOT_FOUND,
                message
            );
            DatagramPacket packet = errorPacket.newDatagram();
            socket.send(packet);
        } catch (IOException e) {
            log.error("{} 发送错误响应失败", LOG_PREFIX, e);
        }
    }
}
