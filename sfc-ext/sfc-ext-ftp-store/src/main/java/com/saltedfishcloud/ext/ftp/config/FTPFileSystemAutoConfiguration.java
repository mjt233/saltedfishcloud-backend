package com.saltedfishcloud.ext.ftp.config;

import com.saltedfishcloud.ext.ftp.filesystem.FTPStorageFactory;
import com.xiaotao.saltedfishcloud.service.file.thumbnail.ThumbnailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FTPFileSystemAutoConfiguration {
    @Autowired
    private ThumbnailService thumbnailService;

    @Bean
    public FTPStorageFactory ftpFileSystemFactory() {
        FTPStorageFactory factory = new FTPStorageFactory();
        factory.setThumbnailService(thumbnailService);
        return factory;
    }

}
