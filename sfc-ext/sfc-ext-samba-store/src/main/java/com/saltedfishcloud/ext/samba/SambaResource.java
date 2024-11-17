package com.saltedfishcloud.ext.samba;

import com.hierynomus.smbj.session.Session;
import com.hierynomus.smbj.share.DiskShare;
import com.hierynomus.smbj.share.File;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.io.AbstractResource;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

@RequiredArgsConstructor
public class SambaResource extends AbstractResource {
    private final SambaDirectRawStoreHandler handler;
    private final String path;

    private String filename;
    private Long lastModified;
    private String smbPath;

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
    public String getDescription() {
        initFileInfo();
        return smbPath;
    }


    @Override
    public InputStream getInputStream() throws IOException {
        Session session = handler.getSession();
        DiskShare diskShare = (DiskShare) session.connectShare(handler.getSambaProperty().getShareName());
        File file = handler.openFileRead(diskShare, path);
        InputStream originInputStream = file.getInputStream();

        return new InputStream() {
            private boolean closed = false;
            @Override
            public int read(@NotNull byte[] b) throws IOException {
                return originInputStream.read(b);
            }

            @Override
            public int read(@NotNull byte[] b, int off, int len) throws IOException {
                return originInputStream.read(b, off, len);
            }

            @Override
            public byte[] readAllBytes() throws IOException {
                return originInputStream.readAllBytes();
            }

            @Override
            public byte[] readNBytes(int len) throws IOException {
                return originInputStream.readNBytes(len);
            }

            @Override
            public int readNBytes(byte[] b, int off, int len) throws IOException {
                return originInputStream.readNBytes(b, off, len);
            }

            @Override
            public long skip(long n) throws IOException {
                return originInputStream.skip(n);
            }

            @Override
            public void skipNBytes(long n) throws IOException {
                originInputStream.skipNBytes(n);
            }

            @Override
            public int available() throws IOException {
                return originInputStream.available();
            }

            @Override
            public void close() throws IOException {
                if (closed) {
                    return;
                }
                closed = true;
                try {
                    originInputStream.close();
                } finally {
                    try {
                        diskShare.close();
                    } finally {
                        handler.returnSession(session);
                    }
                }
            }

            @Override
            public synchronized void mark(int readlimit) {
                originInputStream.mark(readlimit);
            }

            @Override
            public synchronized void reset() throws IOException {
                originInputStream.reset();
            }

            @Override
            public boolean markSupported() {
                return originInputStream.markSupported();
            }

            @Override
            public long transferTo(OutputStream out) throws IOException {
                return originInputStream.transferTo(out);
            }

            @Override
            public int read() throws IOException {
                return originInputStream.read();
            }
        };
    }
}
