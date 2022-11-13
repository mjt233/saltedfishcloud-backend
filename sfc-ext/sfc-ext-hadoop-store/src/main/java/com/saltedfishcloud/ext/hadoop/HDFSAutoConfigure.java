package com.saltedfishcloud.ext.hadoop;

import com.saltedfishcloud.ext.hadoop.filesystem.HDFSFileSystemFactory;
import com.saltedfishcloud.ext.hadoop.store.HDFSStoreHandler;
import com.saltedfishcloud.ext.hadoop.store.HDFSStoreService;
import com.saltedfishcloud.ext.hadoop.store.HDFSStoreServiceFactory;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystemManager;
import com.xiaotao.saltedfishcloud.service.file.FileResourceMd5Resolver;
import com.xiaotao.saltedfishcloud.service.file.thumbnail.ThumbnailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.net.URISyntaxException;

@Configuration
@EnableConfigurationProperties(HDFSProperties.class)
@Slf4j
public class HDFSAutoConfigure {
    @Autowired
    private HDFSProperties properties;
    @Autowired
    private FileResourceMd5Resolver md5Resolver;
    @Autowired
    private DiskFileSystemManager diskFileSystemManager;
    @Autowired
    private ThumbnailService thumbnailService;

    @Bean
    public HDFSFileSystemFactory hdfsFileSystemFactory() {
        HDFSFileSystemFactory fileSystemFactory = new HDFSFileSystemFactory();
        fileSystemFactory.setThumbnailService(thumbnailService);
        diskFileSystemManager.registerFileSystem(fileSystemFactory);
        return fileSystemFactory;
    }

    @Bean
    @ConditionalOnProperty(prefix = "sys.store", name = "type", havingValue = "hdfs")
    public HDFSStoreServiceFactory hdfsStoreServiceFactory() throws IOException, URISyntaxException, InterruptedException {
        return new HDFSStoreServiceFactory(
                new HDFSStoreService(new HDFSStoreHandler(HDFSUtils.getFileSystem(properties)), properties, md5Resolver)
        );
    }

}
