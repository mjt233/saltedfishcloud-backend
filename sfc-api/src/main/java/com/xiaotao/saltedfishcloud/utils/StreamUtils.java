package com.xiaotao.saltedfishcloud.utils;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;

@UtilityClass
@Slf4j
public class StreamUtils {
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
