package com.sfc.nwt.utils;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * 网络接口工具类
 */
@UtilityClass
@Slf4j
public class NetworkUtils {
    private static final ExecutorService IP_TEST_POOL = new ThreadPoolExecutor(
            0,
            5000,
            5, TimeUnit.SECONDS,
            new SynchronousQueue<>(),
            r -> new Thread(r,"ip-test-thread-" + System.currentTimeMillis())
    );

    /**
     * 获取所有已连接网络设备
     */
    public static List<NetworkInterface> getAllConnectedInterface() throws SocketException {
        List<NetworkInterface> res = new ArrayList<>();
        Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
        while (networkInterfaces.hasMoreElements()) {
            NetworkInterface networkInterface = networkInterfaces.nextElement();
            if (!networkInterface.getInterfaceAddresses().isEmpty()) {
                res.add(networkInterface);
            }
        }
        return res;
    }

    /**
     * MAC地址字节码转字符串
     * @param bytes     mac地址字节码
     * @param splitter  分割符
     * @return          十六进制MAC地址
     */
    public static String macByteToString(byte[] bytes, char splitter) {
        if (bytes == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder(bytes.length + bytes.length / 3 + 1);
        for (int i = 0; i < bytes.length; i++) {
            byte curByte = bytes[i];
            sb.append(Integer.toHexString((curByte & 0xf0) >> 4));
            sb.append(Integer.toHexString( curByte & 0x0f ));
            if (i != bytes.length - 1) {
                sb.append(splitter);
            }
        }
        return sb.toString();
    }

    /**
     * 十六进制字符串转二进制字节数组
     * @param hexString 十六进制字符串
     * @return  字节数组
     */
    public static byte[] hexToBinary(String hexString) {
        byte[] res = new byte[hexString.length()/2];

        char[] chars = hexString.toCharArray();
        for (int i = 0; i < chars.length; i+=2) {
            byte highByte = hexToByte(chars[i]);
            byte lowByte = hexToByte(chars[i + 1]);
            res[i/2] = (byte)((highByte << 4) | lowByte);
        }
        return res;
    }

    /**
     * MAC地址字符串转为6字节长度的数据
     * @param mac   mac地址
     * @return      字节数组
     */
    public static byte[] macHexToBinary(String mac) {
        String pureMac = mac.toLowerCase()
                .replaceAll("0[Xx]", "")
                .replaceAll("[:-]", "");


        return hexToBinary(pureMac);
    }

    /**
     * 十六进制字符转byte
     * @param hex   十六进制字符
     * @return      对应的byte
     */
    public static byte hexToByte(char hex) {
        return (byte)( hex < 'a' ? (hex - '0') : (hex - 'a' + 10) );
    }

    /**
     * 构造唤醒对应网卡设备的WOL魔术包
     * @param mac   待唤醒地址
     * @return      魔术包
     */
    public static byte[] getMagicPacket(String mac) {
        int len = 6 + 16*6;
        // 6个0xFF + 重复16次 MAC
        byte[] magicPacket = new byte[len];
        for (int i = 0; i < 6; i++) {
            magicPacket[i] = (byte) 0xff;
        }
        byte[] macBytes = macHexToBinary(mac);
        for (int i = 0; i < 16; i++) {
            System.arraycopy(macBytes, 0, magicPacket, 6 + i*6, 6);
        }
        return magicPacket;
    }

    /**
     * 发送WOL包，远程开机
     * @param targetMac         目标mc地址
     */
    public static void wakeOnLan(String targetMac) throws IOException {
        wakeOnLan(targetMac, 9);
    }

    /**
     * 发送WOL包，远程开机
     * @param targetMac         目标mc地址
     * @throws IOException      IO错误
     */
    public static void wakeOnLan(String targetMac, int port) throws IOException {
        wakeOnLan(targetMac, "255.255.255.255", port);
    }

    /**
     * 发送WOL包，远程开机
     * @param targetMac         目标mc地址
     * @param sendIpAddress     目标ip地址
     * @throws IOException      IO错误
     */
    public static void wakeOnLan(String targetMac, String sendIpAddress, int port) throws IOException {
        try (DatagramSocket socket = new DatagramSocket()) {
            byte[] magicPacketBytes = getMagicPacket(targetMac);
            InetAddress inetAddress = InetAddress.getByName(sendIpAddress);
            DatagramPacket packet = new DatagramPacket(magicPacketBytes, magicPacketBytes.length, inetAddress, port);
            socket.send(packet);
        }
    }

    /**
     * 测试IP地址是否有效
     * @param ipList    待测试IP列表
     * @return          有效IP列表
     */
    public static Set<String> testIpAlive(Collection<String> ipList) {
        Set<String> availableIpSet = new HashSet<>();
        testIpAlive(ipList, ip -> {
            synchronized (availableIpSet) {
                availableIpSet.add(ip);
            }
        });
        return availableIpSet;
    }

    /**
     * 测试IP地址是否有效
     * @param ipList    待测试IP列表
     * @param consumer  有效IP消费函数
     */
    public static void testIpAlive(Collection<String> ipList, Consumer<String> consumer) {
        Semaphore semaphore = new Semaphore(ipList.size());
        try {
            semaphore.acquire(ipList.size());
        } catch (InterruptedException ignore) { }
        for (String ip : ipList) {
            IP_TEST_POOL.submit(() -> {
                try {
                    InetAddress inetAddress = InetAddress.getByName(ip);
                    log.debug("正在测试ip: {} ...", ip);
                    boolean reachable = inetAddress.isReachable(500);
                    log.debug("测试ip: {} 结果: {}", ip, reachable);
                    if (reachable) {
                        consumer.accept(ip);
                    }
                } catch (IOException ignore) { }
                finally {
                    semaphore.release();
                }
            });
        }
        try {
            semaphore.acquire(ipList.size());
        } catch (InterruptedException ignore) { }
    }
}
