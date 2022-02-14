package com.xiaotao.saltedfishcloud.service.file.impl.filesystem;

import com.xiaotao.saltedfishcloud.service.file.DiskFileSystem;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystemProvider;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class DefaultFileSystemProvider implements DiskFileSystemProvider {
    private final DefaultFileSystem defaultFileSystem;
    @Override
    public DiskFileSystem getFileSystem() {
        return defaultFileSystem;
    }
}
