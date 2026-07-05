package com.sfc.pxeboot.server.tftp;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.net.tftp.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;

/**
 * TFTP 读请求处理器，负责处理客户端的文件读取请求，包括选项协商和数据传输。
 */
@Slf4j
public class TftpReadRequestHandler {

    private final TftpFileProvider fileProvider;
    private final int httpPort;

    /**
     * 构造读请求处理器。
     *
     * @param fileProvider 文件提供器
     * @param httpPort     HTTP 服务端口，用于构造 iPXE 脚本中的访问 URL
     */
    public TftpReadRequestHandler(TftpFileProvider fileProvider, int httpPort) {
        this.fileProvider = fileProvider;
        this.httpPort = httpPort;
    }

    /**
     * 处理 TFTP 读请求。
     *
     * @param request     读请求数据包
     * @param serverSocket 服务器监听 socket，用于发送错误响应
     */
    public void handle(TFTPReadRequestPacket request, DatagramSocket serverSocket) {
        String filename = request.getFilename();
        Map<String, String> options = request.getOptions();
        int blockSize = TftpConstants.DEFAULT_BLOCK_SIZE;
        boolean hasTsize = false;

        String blksizeStr = options.get("blksize");
        if (blksizeStr != null) {
            try {
                int requestedBlockSize = Integer.parseInt(blksizeStr);
                blockSize = Math.min(requestedBlockSize, TftpConstants.MAX_BLOCK_SIZE);
                blockSize = Math.max(blockSize, TftpConstants.DEFAULT_BLOCK_SIZE);
            } catch (NumberFormatException e) {
                log.warn("{} 无效的 blksize 选项: {}", TftpConstants.LOG_PREFIX, blksizeStr);
            }
        }
        if (options.containsKey("tsize")) {
            hasTsize = true;
        }

        log.info("{} 收到读请求: {} 来自 {} (blksize={})", TftpConstants.LOG_PREFIX, filename, request.getAddress(), blockSize);

        String baseUrl = "http://" + serverSocket.getLocalAddress().getHostAddress() + ":" + httpPort;
        try (InputStream fileStream = fileProvider.openFileStream(filename, baseUrl)) {
            if (fileStream == null) {
                sendError(serverSocket, request, "file not found: " + filename);
                return;
            }

            try (DatagramSocket dataSocket = new DatagramSocket()) {
                dataSocket.setSoTimeout(5000);

                if (!options.isEmpty()) {
                    long fileSize = -1;
                    if (hasTsize) {
                        try (InputStream sizeStream = fileProvider.openFileStream(filename, baseUrl)) {
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
                        log.warn("{} 未收到 ACK (块号: {}), 传输中断", TftpConstants.LOG_PREFIX, blockNumber);
                        return;
                    }

                    if (bytesRead < blockSize) {
                        log.info("{} 文件传输完成: {} ({} 块)", TftpConstants.LOG_PREFIX, filename, blockNumber);
                        return;
                    }
                    blockNumber++;
                }

                TFTPDataPacket endPacket = new TFTPDataPacket(
                    request.getAddress(), request.getPort(), blockNumber, new byte[0]);
                sendPacket(dataSocket, endPacket);

                TFTPPacket ack = receiveAck(dataSocket, blockNumber, blockSize);
                if (ack == null) {
                    log.warn("{} 未收到终止 ACK (块号: {}), 传输可能未正确结束", TftpConstants.LOG_PREFIX, blockNumber);
                    return;
                }

                log.info("{} 文件传输完成: {} ({} 块)", TftpConstants.LOG_PREFIX, filename, blockNumber);
            }
        } catch (Exception e) {
            log.error("{} 文件传输失败: {}", TftpConstants.LOG_PREFIX, filename, e);
        }
    }

    /**
     * 发送 OACK（Option Acknowledgment）包，向客户端确认协商后的 TFTP 选项。
     *
     * @param dataSocket 数据传输 socket
     * @param request    原始读请求
     * @param blockSize  协商后的块大小
     * @param hasTsize   是否包含 tsize 选项
     * @param fileSize   文件大小（仅当 hasTsize 为 true 时有效）
     * @return true 表示 OACK 发送成功且收到了客户端的 ACK(0)，false 表示传输应终止
     */
    private boolean sendOack(DatagramSocket dataSocket, TFTPReadRequestPacket request,
                             int blockSize, boolean hasTsize, long fileSize) throws IOException {
        byte[] payload = generateOackData(blockSize, hasTsize, fileSize);
        byte[] oackPacket = new byte[2 + payload.length];
        oackPacket[0] = 0;
        oackPacket[1] = (byte) TftpConstants.OACK_OPCODE;
        System.arraycopy(payload, 0, oackPacket, 2, payload.length);

        DatagramPacket dp = new DatagramPacket(
            oackPacket, oackPacket.length, request.getAddress(), request.getPort());
        dataSocket.send(dp);
        log.info("{} 已发送 OACK: blksize={}{}", TftpConstants.LOG_PREFIX, blockSize,
            hasTsize && fileSize >= 0 ? ", tsize=" + fileSize : "");

        TFTPPacket ack = receiveAck(dataSocket, 0, blockSize);
        if (ack == null) {
            log.warn("{} 未收到 OACK 确认 (ACK 0), 传输中断", TftpConstants.LOG_PREFIX);
            return false;
        }
        return true;
    }

    /**
     * 生成 OACK 选项数据。
     *
     * @param blockSize 协商后的块大小
     * @param hasTsize  是否包含 tsize 选项
     * @param fileSize  文件大小
     * @return OACK 选项数据字节数组
     */
    private static byte[] generateOackData(int blockSize, boolean hasTsize, long fileSize) {
        ByteArrayOutputStream oackData = new ByteArrayOutputStream();

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

        return oackData.toByteArray();
    }

    /**
     * 发送 TFTP 数据包。
     *
     * @param socket     发送 socket
     * @param packet     TFTP 数据包
     */
    private void sendPacket(DatagramSocket socket, TFTPDataPacket packet) throws IOException {
        DatagramPacket datagram = packet.newDatagram();
        socket.send(datagram);
    }

    /**
     * 接收 TFTP ACK 确认包。
     *
     * @param socket       接收 socket
     * @param expectedBlock 期望的块号
     * @param blockSize    当前协商的块大小，用于接收缓冲区
     * @return ACK 数据包，超时或不匹配返回 null
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
                    log.error("{} ACK 块号不匹配: 期望 {}, 收到 {}", TftpConstants.LOG_PREFIX, expectedBlock, ack.getBlockNumber());
                    return null;
                }
                return ack;
            }
            return null;
        } catch (SocketTimeoutException e) {
            log.warn("{} 等待 ACK 超时 (块号: {})", TftpConstants.LOG_PREFIX, expectedBlock);
            return null;
        } catch (TFTPPacketException e) {
            log.error("{} 解析 ACK 包失败 (块号: {}): {}", TftpConstants.LOG_PREFIX, expectedBlock, e.getMessage());
            return null;
        }
    }

    /**
     * 发送 TFTP 错误响应。
     *
     * @param serverSocket 服务器监听 socket
     * @param request      原始读请求（用于获取客户端地址）
     * @param message      错误消息
     */
    private void sendError(DatagramSocket serverSocket, TFTPReadRequestPacket request, String message) {
        try {
            TFTPErrorPacket errorPacket = new TFTPErrorPacket(
                request.getAddress(),
                request.getPort(),
                TFTPErrorPacket.FILE_NOT_FOUND,
                message
            );
            DatagramPacket packet = errorPacket.newDatagram();
            serverSocket.send(packet);
        } catch (IOException e) {
            log.error("{} 发送错误响应失败", TftpConstants.LOG_PREFIX, e);
        }
    }
}
