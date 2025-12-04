package com.xiaotao.saltedfishcloud.utils;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import static com.xiaotao.saltedfishcloud.constant.ByteSize._1KiB;

@UtilityClass
@Slf4j
public class StreamUtils {
    public final static int FILE_BUFFER_SIZE = 64 * _1KiB;

    /**
     * 复制流并计算复制的流的数据的md5
     * @param is    输入流
     * @param os    输出流
     * @return  从输入流复制的数据的md5
     */
    public static StreamCopyResult copyStreamAndComputeMd5(InputStream is, OutputStream os) throws IOException {
        return copyStreamAndComputeMd5(is, os, null);
    }


    /**
     * 复制流并计算复制的流的数据的md5
     * @param is    输入流
     * @param os    输出流
     * @param validMd5 待校验的md5。不为null时，复制完成后会将复制的md5与该参数对比，不一致时抛出 {@link IllegalArgumentException} 异常
     * @return  从输入流复制的数据的md5
     */
    public static StreamCopyResult copyStreamAndComputeMd5(InputStream is, OutputStream os, @Nullable String validMd5) throws IOException {
        try {
            byte[] buffer = new byte[FILE_BUFFER_SIZE];
            int len;
            long size = 0;
            MessageDigest md5 = MessageDigest.getInstance("md5");

            // 读取上传的文件数据后，同时计算md5和写入文件
            while ( (len = is.read(buffer, 0, buffer.length)) != -1 ) {
                md5.update(buffer, 0, len);
                os.write(buffer, 0, len);
                size += len;
            }
            String actualMd5 = new String(encodeHex(md5.digest()));
            if (validMd5 != null && !actualMd5.equals(validMd5) ) {
                throw new IllegalArgumentException("md5 " + validMd5 + " is incorrect, actual md5 is " + actualMd5);
            }
            return new StreamCopyResult(size, actualMd5);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
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

    /**
     * 创建一个输出流的close方法修饰类，附加自定义的close逻辑
     * @param outputStream  输出流
     * @param closeable 需要额外附加的自定义的close逻辑
     * @return  新产生的OutputStream修饰类，在原输出流close执行后被调用
     */
    public static OutputStream createCloseActionOutputStream(OutputStream outputStream, Closeable closeable) {
        return new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                outputStream.write(b);
            }

            @Override
            public void write(@NotNull byte[] b) throws IOException {
                outputStream.write(b);
            }

            @Override
            public void write(@NotNull byte[] b, int off, int len) throws IOException {
                outputStream.write(b, off, len);
            }

            @Override
            public void flush() throws IOException {
                outputStream.flush();
            }

            @Override
            public void close() throws IOException {
                outputStream.close();
                closeable.close();
            }
        };
    }
}
