package com.saltedfishcloud.ext.samba;

import com.hierynomus.smbj.session.Session;
import com.hierynomus.smbj.share.DiskShare;
import com.hierynomus.smbj.share.File;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.io.AbstractResource;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class SambaResource extends AbstractResource {
    private final SambaStorage handler;
    private final String path;

    private String filename;
    private Long lastModified;
    private String smbPath;

    public SambaResource(SambaStorage handler, String path, String filename, Long lastModified, String smbPath) {
        this.handler = handler;
        this.path = path;
        this.filename = filename;
        this.lastModified = lastModified;
        this.smbPath = smbPath;
    }

    private void initFileInfo() {
        if (filename == null || lastModified == null || smbPath == null) {
            handler.getDiskShare(share -> {
                try(File file = handler.openFileRead(share, path)) {
                    filename = StringUtils.getURLLastName(file.getUncPath(), "\\");
                    lastModified = file.getFileInformation().getBasicInformation().getLastWriteTime().toDate().getTime();
                    smbPath = file.getPath();
                }
            });
        }
    }

    @Override
    public long lastModified() throws IOException {
        initFileInfo();
        return lastModified;
    }

    @Override
    public String getFilename() {
        initFileInfo();
        return filename;
    }

    @Override
    @NotNull
    public String getDescription() {
        initFileInfo();
        return smbPath;
    }


    @Override
    @NotNull
    public InputStream getInputStream() throws IOException {
        Session session = handler.getSession();
        DiskShare diskShare = null;
        File file = null;
        InputStream originInputStream = null;
        boolean success = false;
        try {
            diskShare = (DiskShare) session.connectShare(handler.getSambaProperty().getShareName());
            file = handler.openFileRead(diskShare, path);
            originInputStream = file.getInputStream();
            success = true;
        } finally {
            if (!success) {
                closeQuietly(originInputStream);
                closeQuietly(file);
                closeQuietly(diskShare);
                handler.returnSession(session);
            }
        }

        File openedFile = file;
        DiskShare openedShare = diskShare;
        InputStream openedInputStream = originInputStream;

        AtomicBoolean closed = new AtomicBoolean();
        return new FilterInputStream(openedInputStream) {
            @Override
            public void close() throws IOException {
                if (!closed.compareAndSet(false, true)) {
                    return;
                }
                super.close();
                try {
                    openedInputStream.close();
                } finally {
                    try {
                        closeQuietly(openedFile);
                    } finally {
                        try {
                            closeQuietly(openedShare);
                        } finally {
                            handler.returnSession(session);
                        }
                    }
                }
            }
        };
    }

    private void closeQuietly(AutoCloseable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (Exception e) {
            log.error("关闭资源失败 {}", closeable, e);
        }
    }
}
