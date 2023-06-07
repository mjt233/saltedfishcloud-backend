package com.sfc.nwt.utils;

import lombok.experimental.UtilityClass;

import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/**
 * 网络接口工具类
 */
@UtilityClass
public class NetworkUtils {
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
     * MAC地址字符串转为字节码
     * @param mac   mac地址
     * @return      字节码
     */
    public static byte[] macHexToByte(String mac) {
        String pureMac = mac.toLowerCase()
                .replaceAll("0[Xx]", "")
                .replaceAll("[:-]", "");

        byte[] res = new byte[pureMac.length()/2];

        char[] chars = pureMac.toCharArray();
        for (int i = 0; i < chars.length; i+=2) {
            byte highByte = hexToByte(chars[i]);
            byte lowByte = hexToByte(chars[i + 1]);
            res[i/2] = (byte)((highByte << 4) | lowByte);
        }
        return res;
    }

    /**
     * 十六进制字符转byte
     * @param hex   十六进制字符
     * @return      对应的byte
     */
    private static byte hexToByte(char hex) {
        return (byte)( hex < 'a' ? (hex - '0') : (hex - 'a' + 10) );
    }
}
