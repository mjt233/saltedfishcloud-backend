package com.xiaotao.saltedfishcloud.service.file.impl.filesystem;

import com.xiaotao.saltedfishcloud.service.file.DiskFileSystem;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystemFactory;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class DefaultFileSystemFactory implements DiskFileSystemFactory {
    private final DefaultFileSystem defaultFileSystem;
    @Override
    public DiskFileSystem getFileSystem() {
        return defaultFileSystem;
    }
}
