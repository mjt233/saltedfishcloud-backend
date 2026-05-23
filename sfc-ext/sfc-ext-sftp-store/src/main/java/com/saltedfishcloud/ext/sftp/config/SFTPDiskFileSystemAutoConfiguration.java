package com.saltedfishcloud.ext.sftp.config;

import com.saltedfishcloud.ext.sftp.filesystem.SFTPStorageFactory;
import com.xiaotao.saltedfishcloud.service.file.thumbnail.ThumbnailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SFTPDiskFileSystemAutoConfiguration {
    @Autowired
    private ThumbnailService thumbnailService;

    @Bean
    public SFTPStorageFactory sftpDiskFileSystemFactory() {
        SFTPStorageFactory factory = new SFTPStorageFactory();
        factory.setThumbnailService(thumbnailService);
        return factory;
    }
}
