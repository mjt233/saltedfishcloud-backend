package com.saltedfishcloud.ext.sftp;

import lombok.*;
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
    private SFTPDirectRawStoreHandler handler;
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
        return new SFTPFileInputStream(sftpClient.open(path), sftpClient);
    }
}
