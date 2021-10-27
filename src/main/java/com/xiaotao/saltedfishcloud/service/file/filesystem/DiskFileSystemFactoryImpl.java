package com.xiaotao.saltedfishcloud.service.file.filesystem;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DiskFileSystemFactoryImpl implements DiskFileSystemFactory {
    private final LocalDiskFileSystem localDiskFileSystem;
    @Override
    public DiskFileSystem getFileSystem() {
        return localDiskFileSystem;
    }
}
