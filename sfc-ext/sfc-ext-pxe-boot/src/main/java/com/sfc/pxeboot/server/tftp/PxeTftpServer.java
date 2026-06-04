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

        try {
            // 从网盘加载文件
            byte[] fileData = loadFileFromDisk(filename);
            if (fileData == null) {
                sendError(request, TFTPErrorPacket.FILE_NOT_FOUND, "文件不存在: " + filename);
                return;
            }

            // 使用 TFTP 客户端发送数据
            TFTPClient client = new TFTPClient();
            client.beginBufferedOps();

            try {
                // 发送文件数据
                int blockNumber = 1;
                int offset = 0;
                while (offset < fileData.length) {
                    int blockSize = Math.min(BLOCK_SIZE, fileData.length - offset);
                    byte[] blockData = new byte[blockSize];
                    System.arraycopy(fileData, offset, blockData, 0, blockSize);

                    TFTPDataPacket dataPacket = new TFTPDataPacket(
                        request.getAddress(),
                        request.getPort(),
                        blockNumber,
                        blockData
                    );

                    // 发送数据包并等待 ACK
                    client.bufferedSend(dataPacket);
                    TFTPPacket ackPacket = client.bufferedReceive();

                    if (ackPacket.getType() != TFTPPacket.ACKNOWLEDGEMENT) {
                        log.error("{} 收到非预期的响应: {}", LOG_PREFIX, ackPacket.getType());
                        break;
                    }

                    TFTPAckPacket ack = (TFTPAckPacket) ackPacket;
                    if (ack.getBlockNumber() != blockNumber) {
                        log.error("{} ACK 块号不匹配: 期望 {}, 收到 {}", LOG_PREFIX, blockNumber, ack.getBlockNumber());
                        break;
                    }

                    offset += blockSize;
                    blockNumber++;
                }

                log.info("{} 文件传输完成: {} ({} 字节)", LOG_PREFIX, filename, fileData.length);
            } finally {
                client.endBufferedOps();
            }

        } catch (IOException | TFTPPacketException e) {
            log.error("{} 文件传输失败: {}", LOG_PREFIX, filename, e);
            sendError(request, TFTPErrorPacket.UNDEFINED, e.getMessage());
        }
    }

    private byte[] loadFileFromDisk(String filename) {
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

            try (InputStream is = resource.getInputStream()) {
                return is.readAllBytes();
            }
        } catch (Exception e) {
            log.error("{} 加载文件失败: {}", LOG_PREFIX, filename, e);
            return null;
        }
    }

    private void sendError(TFTPReadRequestPacket request, int errorCode, String message) {
        try {
            TFTPErrorPacket errorPacket = new TFTPErrorPacket(
                request.getAddress(),
                request.getPort(),
                errorCode,
                message
            );
            DatagramPacket packet = errorPacket.newDatagram();
            socket.send(packet);
        } catch (IOException e) {
            log.error("{} 发送错误响应失败", LOG_PREFIX, e);
        }
    }
}
