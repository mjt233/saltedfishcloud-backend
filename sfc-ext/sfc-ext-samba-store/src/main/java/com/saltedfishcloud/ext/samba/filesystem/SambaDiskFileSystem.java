package com.saltedfishcloud.ext.samba.filesystem;

import com.saltedfishcloud.ext.samba.SambaDirectRawStoreHandler;
import com.xiaotao.saltedfishcloud.service.file.RawDiskFileSystem;

import java.io.Closeable;
import java.io.IOException;

public class SambaDiskFileSystem extends RawDiskFileSystem implements Closeable {
    private final SambaDirectRawStoreHandler handler;
    public SambaDiskFileSystem(SambaDirectRawStoreHandler storeHandler, String basePath) throws IOException {
        super(storeHandler, basePath);
        this.handler = storeHandler;
    }

    @Override
    public void close() throws IOException {
        handler.close();
    }
}
