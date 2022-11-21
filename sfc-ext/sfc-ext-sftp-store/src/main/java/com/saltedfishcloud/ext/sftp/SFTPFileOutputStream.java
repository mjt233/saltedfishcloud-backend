package com.saltedfishcloud.ext.sftp;

import net.schmizz.sshj.sftp.RemoteFile;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.OutputStream;

public class SFTPFileOutputStream extends OutputStream {
    private final RemoteFile file;
    private final OutputStream os;


    public SFTPFileOutputStream(RemoteFile file) {
        this.file = file;
        os = this.file.new RemoteFileOutputStream();
    }

    @Override
    public void write(int b) throws IOException {
        os.write(b);
    }

    @Override
    public void write(@NotNull byte[] b) throws IOException {
        os.write(b);
    }

    @Override
    public void write(@NotNull byte[] b, int off, int len) throws IOException {
        os.write(b, off, len);
    }

    @Override
    public void close() throws IOException {
        os.close();
        file.close();
    }
}
