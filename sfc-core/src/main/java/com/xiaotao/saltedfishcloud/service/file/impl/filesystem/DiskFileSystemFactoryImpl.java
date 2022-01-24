package com.xiaotao.saltedfishcloud.service.file.impl.filesystem;

import com.xiaotao.saltedfishcloud.service.file.DiskFileSystem;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystemFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
public class DiskFileSystemFactoryImpl implements DiskFileSystemFactory {
    private final LocalDiskFileSystem localDiskFileSystem;
    @Override
    public DiskFileSystem getFileSystem() {
        return localDiskFileSystem;
    }
}
