package com.sfc.ext.oss.config;

import com.sfc.ext.oss.OSSDiskFileSystemFactory;
import com.sfc.ext.oss.constants.OSSType;
import com.sfc.ext.oss.store.S3DirectRawHandler;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystemManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class OSSAutoConfiguration implements InitializingBean {
    @Autowired
    private DiskFileSystemManager diskFileSystemManager;

    @Override
    public void afterPropertiesSet() throws Exception {
        diskFileSystemManager.registerFileSystem(ossDiskFileSystemFactory());
    }

    @Bean
    public OSSDiskFileSystemFactory ossDiskFileSystemFactory() {
        log.info("OSS存储支持~");
        OSSDiskFileSystemFactory ossDiskFileSystemFactory = new OSSDiskFileSystemFactory();
        ossDiskFileSystemFactory.registerOSSStoreType(OSSType.S3, S3DirectRawHandler::new);
        return ossDiskFileSystemFactory;
    }
}
