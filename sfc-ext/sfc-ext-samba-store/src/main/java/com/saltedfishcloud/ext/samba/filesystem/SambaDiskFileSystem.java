package com.saltedfishcloud.ext.samba.filesystem;

import com.saltedfishcloud.ext.samba.SambaStorage;
import com.xiaotao.saltedfishcloud.service.file.RawDiskFileSystem;

import java.io.IOException;

public class SambaDiskFileSystem extends RawDiskFileSystem {
    private final SambaStorage handler;
    public SambaDiskFileSystem(SambaStorage storeHandler, String basePath) throws IOException {
        super(storeHandler, basePath);
        this.handler = storeHandler;
    }

    @Override
    public void close() throws IOException {
        handler.close();
    }
}
