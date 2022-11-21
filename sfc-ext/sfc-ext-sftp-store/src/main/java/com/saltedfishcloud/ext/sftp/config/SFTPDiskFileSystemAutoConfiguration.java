package com.saltedfishcloud.ext.sftp.config;

import com.saltedfishcloud.ext.sftp.filesystem.SFTPDiskFileSystemFactory;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystemManager;
import com.xiaotao.saltedfishcloud.service.file.thumbnail.ThumbnailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SFTPDiskFileSystemAutoConfiguration {
    @Autowired
    private ThumbnailService thumbnailService;

    @Autowired
    private DiskFileSystemManager diskFileSystemManager;

    @Bean
    public SFTPDiskFileSystemFactory sftpDiskFileSystemFactory() {
        SFTPDiskFileSystemFactory factory = new SFTPDiskFileSystemFactory();
        factory.setThumbnailService(thumbnailService);
        diskFileSystemManager.registerFileSystem(factory);
        return factory;
    }
}
