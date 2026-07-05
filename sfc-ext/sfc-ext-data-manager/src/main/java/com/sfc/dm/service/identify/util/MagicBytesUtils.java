package com.sfc.dm.service.identify.util;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * 文件头 magic bytes 读取与匹配工具类
 */
public class MagicBytesUtils {

    private MagicBytesUtils() {}

    /**
     * 读取文件头部指定长度的字节
     * @param file 目标文件
     * @param length 需要读取的字节数
     * @return 文件头字节数组，文件不足指定长度时返回实际读取的字节
     * @throws IOException 读取失败时抛出
     */
    public static byte[] readHeader(File file, int length) throws IOException {
        byte[] header = new byte[length];
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            int read = raf.read(header, 0, length);
            if (read < length) {
                byte[] result = new byte[read];
                System.arraycopy(header, 0, result, 0, read);
                return result;
            }
        }
        return header;
    }

    /**
     * 从指定偏移位置读取文件内容
     * @param file 目标文件
     * @param offset 起始偏移量
     * @param length 需要读取的字节数
     * @return 读取到的字节数组
     * @throws IOException 读取失败时抛出
     */
    public static byte[] readAt(File file, long offset, int length) throws IOException {
        byte[] buffer = new byte[length];
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            raf.seek(offset);
            int read = raf.read(buffer, 0, length);
            if (read < length) {
                byte[] result = new byte[read];
                System.arraycopy(buffer, 0, result, 0, read);
                return result;
            }
        }
        return buffer;
    }

    /**
     * 检查 header 字节数组在指定偏移处是否匹配 magic 字节
     * @param header 文件头字节数组
     * @param magic 待匹配的 magic 字节序列
     * @param offset 起始偏移量
     * @return 是否匹配
     */
    public static boolean matchMagic(byte[] header, byte[] magic, int offset) {
        if (header == null || header.length < offset + magic.length) {
            return false;
        }
        for (int i = 0; i < magic.length; i++) {
            if (header[offset + i] != magic[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * 检查文件头是否以指定 magic 字节开头（偏移为 0）
     * @param header 文件头字节数组
     * @param magic 待匹配的 magic 字节序列
     * @return 是否匹配
     */
    public static boolean matchMagic(byte[] header, byte[] magic) {
        return matchMagic(header, magic, 0);
    }
}
