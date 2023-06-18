package com.saltedfishcloud.ext.sftp;

import net.schmizz.sshj.sftp.RemoteFile;
import net.schmizz.sshj.sftp.SFTPClient;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;

public class SFTPFileInputStream extends InputStream {
    private final RemoteFile file;
    private final InputStream is;
    private final SFTPClient client;

    public SFTPFileInputStream(RemoteFile file, SFTPClient client) {
        this.file = file;
        is = this.file.new RemoteFileInputStream();
        this.client = client;
    }

    @Override
    public int read() throws IOException {
        return is.read();
    }

    @Override
    public int read(@NotNull byte[] b) throws IOException {
        return is.read(b);
    }

    @Override
    public int read(@NotNull byte[] b, int off, int len) throws IOException {
        return is.read(b, off, len);
    }

    @Override
    public long skip(long n) throws IOException {
        return is.skip(n);
    }

    @Override
    public int available() throws IOException {
        return is.available();
    }

    @Override
    public synchronized void mark(int readlimit) {
        is.mark(readlimit);
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
    public void close() throws IOException {
        try {
            is.close();
            file.close();
        } finally {
            client.close();
        }
    }
}
