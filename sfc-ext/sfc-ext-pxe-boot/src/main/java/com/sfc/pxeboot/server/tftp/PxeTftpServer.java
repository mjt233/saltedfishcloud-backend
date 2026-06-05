package com.sfc.pxeboot.server.tftp;

import com.sfc.pxeboot.PxeBootProperty;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystemManager;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.net.tftp.*;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.SmartLifecycle;
import org.springframework.core.io.Resource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * PXE TFTP 服务器
 * 使用 Apache Commons Net 的 TFTP 类实现
 */
@Slf4j
public class PxeTftpServer implements SmartLifecycle {

    private static final String LOG_PREFIX = "[PXE-TFTP]";
    private static final int DEFAULT_BLOCK_SIZE = 512;
    private static final int MAX_BLOCK_SIZE = 65464;

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
        byte[] buffer = new byte[MAX_BLOCK_SIZE + 4];
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
        Map<String, String> options = request.getOptions();
        int blockSize = DEFAULT_BLOCK_SIZE;
        boolean hasTsize = false;

        // 解析客户端请求的 TFTP 选项
        String blksizeStr = options.get("blksize");
        if (blksizeStr != null) {
            try {
                int requestedBlockSize = Integer.parseInt(blksizeStr);
                blockSize = Math.min(requestedBlockSize, MAX_BLOCK_SIZE);
                blockSize = Math.max(blockSize, DEFAULT_BLOCK_SIZE);
            } catch (NumberFormatException e) {
                log.warn("{} 无效的 blksize 选项: {}", LOG_PREFIX, blksizeStr);
            }
        }
        if (options.containsKey("tsize")) {
            hasTsize = true;
        }

        log.info("{} 收到读请求: {} 来自 {} (blksize={})", LOG_PREFIX, filename, request.getAddress(), blockSize);

        try (InputStream fileStream = openFileStream(filename)) {
            if (fileStream == null) {
                sendError(request, "文件不存在: " + filename);
                return;
            }

            try (DatagramSocket dataSocket = new DatagramSocket()) {
                dataSocket.setSoTimeout(5000);

                // 如果客户端请求了选项，先发送 OACK 进行选项协商
                if (!options.isEmpty()) {
                    long fileSize = -1;
                    if (hasTsize) {
                        try (InputStream sizeStream = openFileStream(filename)) {
                            if (sizeStream != null) {
                                fileSize = 0;
                                byte[] skipBuf = new byte[8192];
                                int n;
                                while ((n = sizeStream.read(skipBuf)) != -1) {
                                    fileSize += n;
                                }
                            }
                        }
                    }

                    if (!sendOack(dataSocket, request, blockSize, hasTsize, fileSize)) {
                        return;
                    }
                }

                byte[] buffer = new byte[blockSize];
                int bytesRead;
                int blockNumber = 1;

                while ((bytesRead = fileStream.read(buffer)) != -1) {
                    byte[] data = Arrays.copyOf(buffer, bytesRead);

                    TFTPDataPacket dataPacket = new TFTPDataPacket(
                        request.getAddress(), request.getPort(), blockNumber, data);
                    sendPacket(dataSocket, dataPacket);

                    TFTPPacket ack = receiveAck(dataSocket, blockNumber, blockSize);
                    if (ack == null) {
                        log.warn("{} 未收到 ACK (块号: {}), 传输中断", LOG_PREFIX, blockNumber);
                        return;
                    }

                    if (bytesRead < blockSize) {
                        log.info("{} 文件传输完成: {} ({} 块)", LOG_PREFIX, filename, blockNumber);
                        return;
                    }
                    blockNumber++;
                }

                // 文件大小正好是块大小的整数倍（或文件为空），
                // 发送0字节数据包标识传输结束，符合 RFC 1350
                TFTPDataPacket endPacket = new TFTPDataPacket(
                    request.getAddress(), request.getPort(), blockNumber, new byte[0]);
                sendPacket(dataSocket, endPacket);

                TFTPPacket ack = receiveAck(dataSocket, blockNumber, blockSize);
                if (ack == null) {
                    log.warn("{} 未收到终止 ACK (块号: {}), 传输可能未正确结束", LOG_PREFIX, blockNumber);
                    return;
                }

                log.info("{} 文件传输完成: {} ({} 块)", LOG_PREFIX, filename, blockNumber);
            }
        } catch (Exception e) {
            log.error("{} 文件传输失败: {}", LOG_PREFIX, filename, e);
        }
    }

    /**
     * 发送 OACK（Option Acknowledgment）包，向客户端确认协商后的 TFTP 选项
     *
     * @param dataSocket  数据传输 socket
     * @param request     原始读请求
     * @param blockSize   协商后的块大小
     * @param hasTsize    是否包含 tsize 选项
     * @param fileSize    文件大小（仅当 hasTsize 为 true 时有效）
     * @return true 表示 OACK 发送成功且收到了客户端的 ACK(0)，false 表示传输应终止
     */
    private boolean sendOack(DatagramSocket dataSocket, TFTPReadRequestPacket request,
                             int blockSize, boolean hasTsize, long fileSize) throws IOException {
        byte[] payload = generateOackData(blockSize, hasTsize, fileSize);
        byte[] oackPacket = new byte[2 + payload.length];
        oackPacket[0] = 0;
        oackPacket[1] = 6; // OACK opcode
        System.arraycopy(payload, 0, oackPacket, 2, payload.length);

        DatagramPacket dp = new DatagramPacket(
            oackPacket, oackPacket.length, request.getAddress(), request.getPort());
        dataSocket.send(dp);
        log.info("{} 已发送 OACK: blksize={}{}", LOG_PREFIX, blockSize,
            hasTsize && fileSize >= 0 ? ", tsize=" + fileSize : "");

        // 等待客户端回复 ACK(0) 确认选项
        TFTPPacket ack = receiveAck(dataSocket, 0, blockSize);
        if (ack == null) {
            log.warn("{} 未收到 OACK 确认 (ACK 0), 传输中断", LOG_PREFIX);
            return false;
        }
        return true;
    }

    @NotNull
    private static byte[] generateOackData(int blockSize, boolean hasTsize, long fileSize) {
        ByteArrayOutputStream oackData = new ByteArrayOutputStream();

        // 构造 OACK 数据：每个选项以 null 结尾的 key-value 字符串对
        byte[] blksizeKey = "blksize".getBytes(StandardCharsets.US_ASCII);
        byte[] blksizeVal = String.valueOf(blockSize).getBytes(StandardCharsets.US_ASCII);
        oackData.write(blksizeKey, 0, blksizeKey.length);
        oackData.write(0);
        oackData.write(blksizeVal, 0, blksizeVal.length);
        oackData.write(0);

        if (hasTsize && fileSize >= 0) {
            byte[] tsizeKey = "tsize".getBytes(StandardCharsets.US_ASCII);
            byte[] tsizeVal = String.valueOf(fileSize).getBytes(StandardCharsets.US_ASCII);
            oackData.write(tsizeKey, 0, tsizeKey.length);
            oackData.write(0);
            oackData.write(tsizeVal, 0, tsizeVal.length);
            oackData.write(0);
        }

        // 构造 OACK 数据包：opcode=6 + 选项数据
        return oackData.toByteArray();
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
     *
     * @param socket      接收 socket
     * @param expectedBlock 期望的块号
     * @param blockSize   当前协商的块大小，用于接收缓冲区
     */
    private TFTPPacket receiveAck(DatagramSocket socket, int expectedBlock, int blockSize) throws IOException {
        try {
            byte[] buf = new byte[blockSize + 4];
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
