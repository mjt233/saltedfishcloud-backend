package com.saltedfishcloud.ext.minio.config;

import com.saltedfishcloud.ext.minio.filesystem.MinioFileSystemFactory;
import com.xiaotao.saltedfishcloud.service.file.thumbnail.ThumbnailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class MinioFileSystemAutoConfiguration {

    @Autowired
    private ThumbnailService thumbnailService;

    @Bean
    public MinioFileSystemFactory minioFileSystemFactory() {
        MinioFileSystemFactory minioFileSystemFactory = new MinioFileSystemFactory();
        minioFileSystemFactory.setThumbnailService(thumbnailService);
        return minioFileSystemFactory;
    }
}
