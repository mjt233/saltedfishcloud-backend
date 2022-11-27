package com.saltedfishcloud.ext.ftp.config;

import com.saltedfishcloud.ext.ftp.filesystem.FTPFileSystemFactory;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystemManager;
import com.xiaotao.saltedfishcloud.service.file.thumbnail.ThumbnailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FTPFileSystemAutoConfiguration {
    @Autowired
    private DiskFileSystemManager diskFileSystemManager;
    @Autowired
    private ThumbnailService thumbnailService;

    @Bean
    public FTPFileSystemFactory ftpFileSystemFactory() {
        FTPFileSystemFactory factory = new FTPFileSystemFactory();
        factory.setThumbnailService(thumbnailService);
        diskFileSystemManager.registerFileSystem(factory);
        return factory;
    }

}
