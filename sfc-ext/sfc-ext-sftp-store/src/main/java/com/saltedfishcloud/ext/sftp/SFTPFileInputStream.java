package com.saltedfishcloud.ext.sftp;

import net.schmizz.sshj.sftp.RemoteFile;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;

public class SFTPFileInputStream extends InputStream {
    private final RemoteFile file;
    private final InputStream is;

    public SFTPFileInputStream(RemoteFile file) {
        this.file = file;
        is = this.file.new RemoteFileInputStream();
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
        is.close();
        file.close();
    }
}
