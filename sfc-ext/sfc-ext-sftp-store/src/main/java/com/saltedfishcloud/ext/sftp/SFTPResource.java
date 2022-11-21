package com.saltedfishcloud.ext.sftp;

import com.saltedfishcloud.ext.sftp.config.SFTPProperty;
import lombok.*;
import org.springframework.beans.BeanUtils;
import org.springframework.core.io.AbstractResource;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

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
        return new SFTPFileInputStream(handler.getSFTPClient().open(path));
    }
}
