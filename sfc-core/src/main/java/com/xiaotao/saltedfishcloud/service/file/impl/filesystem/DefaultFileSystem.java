package com.xiaotao.saltedfishcloud.service.file.impl.filesystem;

import com.xiaotao.saltedfishcloud.service.file.DiskFileSystem;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystemFactory;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class DefaultFileSystem implements DiskFileSystemFactory {
    private final DefaultDiskFileSystem defaultDiskFileSystem;
    @Override
    public DiskFileSystem getFileSystem() {
        return defaultDiskFileSystem;
    }
}
