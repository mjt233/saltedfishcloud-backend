package com.saltedfishcloud.ext.ftp;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPFileFilter;
import org.apache.commons.pool2.ObjectPool;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;

/**
 * FTP会话，关联对象池和原始FTP客户端对象
 */
@Slf4j
public class FTPSession extends FTPClient implements Closeable {
    private static final String SERVER_CHARSET = "ISO-8859-1";
    private final ObjectPool<FTPSession> pool;

    public FTPSession(ObjectPool<FTPSession> pool) {
        log.debug("[FTP Session]FTP会话实例化：{}", this.hashCode());
        this.pool = pool;
    }

    @Override
    public void close() throws IOException {
        try {
            log.debug("[FTP Session]FTP会话归还:{}", this.hashCode());
            pool.returnObject(this);
        } catch (Exception e) {
            this.disconnect();
            throw new IOException("FTP会话归还连接池失败", e);
        }
    }

    public void cd(String path) throws IOException {
        if (!this.changeWorkingDirectory(path)) {
            throw new IOException("切换目录失败：" + path);
        }
    }

    @Override
    public FTPFile[] listFiles(String pathname) throws IOException {
        return super.listFiles(toServerCharset(pathname));
    }

    private String toServerCharset(String str) throws UnsupportedEncodingException {
        return new String(str.replaceAll("/", "\\\\").getBytes(), SERVER_CHARSET);
    }

    @Override
    public InputStream retrieveFileStream(String remote) throws IOException {
        return super.retrieveFileStream(toServerCharset(remote));
    }



    @Override
    public FTPFile[] listFiles(String pathname, FTPFileFilter filter) throws IOException {
        String path = toServerCharset(pathname);
        log.debug("[FTP Session]列出文件：{}", path);
        return super.listFiles(path, filter);
    }
}
