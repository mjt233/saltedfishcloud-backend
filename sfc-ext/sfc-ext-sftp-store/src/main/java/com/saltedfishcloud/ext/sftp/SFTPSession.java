package com.saltedfishcloud.ext.sftp;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.sftp.SFTPClient;
import net.schmizz.sshj.sftp.SFTPEngine;
import org.apache.commons.pool2.ObjectPool;

import java.io.Closeable;
import java.io.IOException;

/**
 * SFTP会话，维护SSHClient以及其对应的SFTPClient关系以便统一关闭
 */
@Getter
@Slf4j
public class SFTPSession extends SFTPClient implements Closeable {
    private final static String LOG_PREFIX = "[SFTPSession]";
    private final SSHClient sshClient;
    private final ObjectPool<SFTPSession> pool;

    public SFTPSession(SSHClient sshClient, ObjectPool<SFTPSession> pool) throws IOException {
        super(new SFTPEngine(sshClient).init());
        this.sshClient = sshClient;
        this.pool = pool;
    }

    @Override
    public void close() throws IOException {
        try {
            log.debug("{}归还对象回对象池", LOG_PREFIX);
            pool.returnObject(this);
        } catch (Exception e) {
            log.error("{}归还对象失败", LOG_PREFIX);
            this.closeSession();
            throw new IOException(e);
        }
    }

    public void closeSession() throws IOException {
        super.close();
        sshClient.close();
    }

}
