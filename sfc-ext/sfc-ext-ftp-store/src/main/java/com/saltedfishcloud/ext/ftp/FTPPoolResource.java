package com.saltedfishcloud.ext.ftp;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.net.ftp.FTPFile;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.io.AbstractResource;

import java.io.IOException;
import java.io.InputStream;

@Slf4j
public class FTPPoolResource extends AbstractResource {
    private final static String LOG_PREFIX = "[FTP Resource]";
    private final FTPFile file;
    private final FTPDirectRawStoreHandler handler;
    private final String fullPath;

    public FTPPoolResource(FTPDirectRawStoreHandler handler, FTPFile file, String fullPath) {
        this.handler = handler;
        this.file = file;
        this.fullPath = fullPath;
    }

    @Override
    public String getDescription() {
        return fullPath;
    }

    @Override
    public long contentLength() throws IOException {
        return file.getSize();
    }

    @Override
    public long lastModified() throws IOException {
        return file.getTimestamp().getTimeInMillis();
    }

    @Override
    public InputStream getInputStream() throws IOException {
        FTPSession session = handler.getSession();
        final InputStream is = session.retrieveFileStream(fullPath);
        if (is == null) {
            session.close();
            throw new IOException("获取文件流失败：" + fullPath);
        }
        return new InputStream() {
            @Override
            public int read(@NotNull byte[] b) throws IOException {
                return is.read(b);
            }

            @Override
            public int read(@NotNull byte[] b, int off, int len) throws IOException {
                return is.read(b, off, len);
            }

            @Override
            public byte[] readAllBytes() throws IOException {
                return is.readAllBytes();
            }

            @Override
            public int available() throws IOException {
                return is.available();
            }

            @Override
            public void close() throws IOException {
                is.close();
                if(!session.completePendingCommand()) {
                    log.error("{}传输会话关闭失败", LOG_PREFIX);
                }
                session.close();
            }

            @Override
            public synchronized void reset() throws IOException {
                is.reset();
            }

            @Override
            public boolean markSupported() {
                return is.markSupported();
            }

            @Override
            public int read() throws IOException {
                return is.read();
            }

            @Override
            public long skip(long n) throws IOException {
                return is.skip(n);
            }
        };
    }
}
