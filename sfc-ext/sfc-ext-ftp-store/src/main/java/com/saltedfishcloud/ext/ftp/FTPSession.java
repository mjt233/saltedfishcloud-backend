package com.saltedfishcloud.ext.ftp;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.pool2.ObjectPool;

import java.io.Closeable;
import java.io.IOException;

/**
 * FTP会话，关联对象池和原始FTP客户端对象
 */
@Slf4j
public class FTPSession extends FTPClient implements Closeable {
    private final ObjectPool<FTPSession> pool;

    public FTPSession(ObjectPool<FTPSession> pool) {
        this.pool = pool;
    }

    @Override
    public void close() throws IOException {
        try {
            log.debug("[FTP Session]FTP会话归还");
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
}
