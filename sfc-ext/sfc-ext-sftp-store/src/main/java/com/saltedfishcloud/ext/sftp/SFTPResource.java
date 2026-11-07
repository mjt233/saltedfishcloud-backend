package com.saltedfishcloud.ext.sftp;

import lombok.*;
import net.schmizz.sshj.sftp.RemoteFile;
import net.schmizz.sshj.sftp.SFTPClient;
import org.springframework.core.io.AbstractResource;

import java.io.IOException;
import java.io.InputStream;

@EqualsAndHashCode(callSuper = true)
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SFTPResource extends AbstractResource {
    private SFTPStorage handler;
    private String path;
    private long size;
    private long lastModified;
    private String name;

    @Override
    public long contentLength() throws IOException {
        return size;
    }

    @Override
    public long lastModified() throws IOException {
        return lastModified;
    }

    @Override
    public String getFilename() {
        return name;
    }

    @Override
    public String getDescription() {
        return "sftp://" + handler.getProperty().getHost() + ":" + handler.getProperty().getPort() + "/" + path;
    }

    @Override
    public InputStream getInputStream() throws IOException {
        SFTPClient sftpClient = handler.getSFTPClient();
        RemoteFile remoteFile;
        try {
            remoteFile = sftpClient.open(path);
        } catch (IOException | RuntimeException e) {
            // 打开文件失败时需要先把借出的会话归还连接池，避免会话泄漏
            try {
                sftpClient.close();
            } catch (IOException closeException) {
                e.addSuppressed(closeException);
            }
            throw e;
        }
        return new SFTPFileInputStream(remoteFile, sftpClient);
    }
}
