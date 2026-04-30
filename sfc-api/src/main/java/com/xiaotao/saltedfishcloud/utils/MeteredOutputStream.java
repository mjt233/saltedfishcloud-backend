package com.xiaotao.saltedfishcloud.utils;

import lombok.Getter;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 对 OutputStream 进行包装，拦截所有 write 操作，统计写入的总字节数及已写入数据的 MD5 值。
 * 可在流使用过程中随时获取当前统计结果。
 */
public class MeteredOutputStream extends FilterOutputStream {
    private final MessageDigest md5;
    /**
     * -- GETTER --
     * 已写入的总字节数
     */
    @Getter
    private long bytesWritten;

    public MeteredOutputStream(OutputStream out) {
        super(out);
        try {
            this.md5 = MessageDigest.getInstance("md5");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void write(int b) throws IOException {
        out.write(b);
        md5.update((byte) b);
        bytesWritten++;
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        out.write(b, off, len);
        md5.update(b, off, len);
        bytesWritten += len;
    }

    /** 已写入数据的 MD5 十六进制字符串 */
    public String getMd5() {
        return new String(encodeHex(md5.digest()));
    }

    private static char[] encodeHex(byte[] bytes) {
        char[] HEX_CHARS = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};
        char[] chars = new char[32];
        for (int i = 0; i < chars.length; i = i + 2) {
            byte b = bytes[i / 2];
            chars[i] = HEX_CHARS[(b >>> 0x4) & 0xf];
            chars[i + 1] = HEX_CHARS[b & 0xf];
        }
        return chars;
    }
}
