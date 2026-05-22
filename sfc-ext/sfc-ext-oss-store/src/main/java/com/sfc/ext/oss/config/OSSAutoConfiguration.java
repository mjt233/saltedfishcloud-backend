package com.sfc.ext.oss.config;

import com.sfc.ext.oss.OSSStorageFactory;
import com.sfc.ext.oss.constants.OSSType;
import com.sfc.ext.oss.store.S3Storage;
import com.xiaotao.saltedfishcloud.service.file.thumbnail.ThumbnailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class OSSAutoConfiguration {

    @Bean
    public OSSStorageFactory ossDiskFileSystemFactory(ThumbnailService thumbnailService) {
        log.info("OSS存储支持~");
        OSSStorageFactory ossDiskFileSystemFactory = new OSSStorageFactory(thumbnailService);
        ossDiskFileSystemFactory.registerOSSStoreType(OSSType.S3, S3Storage::new);
        return ossDiskFileSystemFactory;
    }
}
