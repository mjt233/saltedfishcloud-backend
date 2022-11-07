package com.saltedfishcloud.ext.hadoop.filesystem;

import com.xiaotao.saltedfishcloud.service.file.RawDiskFileSystem;
import com.xiaotao.saltedfishcloud.service.file.store.DirectRawStoreHandler;
import org.apache.hadoop.fs.FileSystem;

import java.io.Closeable;
import java.io.IOException;

public class HDFSFileSystem extends RawDiskFileSystem implements Closeable {
    private FileSystem fs;
    public HDFSFileSystem(DirectRawStoreHandler storeHandler, String basePath, FileSystem fs) {
        super(storeHandler, basePath);
        this.fs = fs;
    }

    @Override
    public void close() throws IOException {
        if (fs != null) {
            fs.close();
        }
    }
}
