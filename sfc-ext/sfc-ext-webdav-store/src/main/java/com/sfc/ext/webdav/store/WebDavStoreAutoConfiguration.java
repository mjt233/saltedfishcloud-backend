package com.sfc.ext.webdav.store;

import com.sfc.ext.webdav.store.filesystem.WebDavStoreFileSystemFactory;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystemManager;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WebDavStoreAutoConfiguration {
    public WebDavStoreAutoConfiguration(DiskFileSystemManager diskFileSystemManager) {
        diskFileSystemManager.registerFileSystem(new WebDavStoreFileSystemFactory());
    }
}
