package com.sfc.dm.service.identify.util;

import java.io.*;
import java.nio.charset.Charset;

/**
 * 文本文件编码检测工具，基于 BOM 标记和字节统计
 */
public class EncodingDetector {

    private EncodingDetector() {}

    /**
     * 检测文件编码
     * @param file 待检测文件
     * @return 编码名称（如 UTF-8、GBK、ISO-8859-1）
     */
    public static String detect(File file) {
        try {
            byte[] header = MagicBytesUtils.readHeader(file, 3);
            if (header.length >= 3
                    && (header[0] & 0xFF) == 0xEF
                    && (header[1] & 0xFF) == 0xBB
                    && (header[2] & 0xFF) == 0xBF) {
                return "UTF-8 BOM";
            }
            if (header.length >= 2
                    && (header[0] & 0xFF) == 0xFF
                    && (header[1] & 0xFF) == 0xFE) {
                return "UTF-16 LE";
            }
            if (header.length >= 2
                    && (header[0] & 0xFF) == 0xFE
                    && (header[1] & 0xFF) == 0xFF) {
                return "UTF-16 BE";
            }
        } catch (IOException ignored) {
        }

        // 读取前 8KB 进行编码推断
        try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file))) {
            byte[] buf = new byte[8192];
            int len = bis.read(buf);
            if (len <= 0) {
                return "UTF-8";
            }
            return guessEncoding(buf, len);
        } catch (IOException e) {
            return "Unknown";
        }
    }

    /**
     * 根据字节特征推断编码
     */
    private static String guessEncoding(byte[] buf, int len) {
        boolean hasHighByte = false;
        boolean validUtf8 = true;
        int i = 0;

        while (i < len) {
            int b = buf[i] & 0xFF;
            if (b <= 0x7F) {
                i++;
            } else if ((b & 0xE0) == 0xC0) {
                if (i + 1 >= len || (buf[i + 1] & 0xC0) != 0x80) {
                    validUtf8 = false;
                    break;
                }
                i += 2;
                hasHighByte = true;
            } else if ((b & 0xF0) == 0xE0) {
                if (i + 2 >= len || (buf[i + 1] & 0xC0) != 0x80 || (buf[i + 2] & 0xC0) != 0x80) {
                    validUtf8 = false;
                    break;
                }
                i += 3;
                hasHighByte = true;
            } else if ((b & 0xF8) == 0xF0) {
                if (i + 3 >= len || (buf[i + 1] & 0xC0) != 0x80
                        || (buf[i + 2] & 0xC0) != 0x80 || (buf[i + 3] & 0xC0) != 0x80) {
                    validUtf8 = false;
                    break;
                }
                i += 4;
                hasHighByte = true;
            } else {
                validUtf8 = false;
                break;
            }
        }

        if (validUtf8 && !hasHighByte) {
            return "ASCII";
        }
        if (validUtf8) {
            return "UTF-8";
        }
        return "GBK";
    }
}
