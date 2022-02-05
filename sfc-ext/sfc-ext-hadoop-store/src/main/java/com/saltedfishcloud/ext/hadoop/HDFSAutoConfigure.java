package com.saltedfishcloud.ext.hadoop;

import com.saltedfishcloud.ext.hadoop.store.HDFSStoreService;
import com.saltedfishcloud.ext.hadoop.store.HDFSStoreServiceFactory;
import com.xiaotao.saltedfishcloud.service.file.StoreService;
import org.apache.hadoop.fs.FileSystem;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

@Configuration
@ConditionalOnProperty(prefix = "sys.store", name = "type", havingValue = "hdfs")
@EnableConfigurationProperties(HDFSProperties.class)
public class HDFSAutoConfigure {
    @Autowired
    private HDFSProperties properties;

    @Bean
    public FileSystem hadoopFileSystem() throws IOException, URISyntaxException, InterruptedException {
        final org.apache.hadoop.conf.Configuration conf = new org.apache.hadoop.conf.Configuration();
        conf.set("fs.defaultFS", properties.getUrl());
        conf.set("hadoop.root.logger", "WARN");
        conf.set("fs.hdfs.impl", "org.apache.hadoop.hdfs.DistributedFileSystem");
        return FileSystem.get(new URI(properties.getUrl()), conf, properties.getUser());
    }

    @Bean
    public StoreService storeService() {
        return new HDFSStoreService();
    }

    @Bean
    public HDFSStoreServiceFactory hdfsStoreServiceFactory() {
        return new HDFSStoreServiceFactory();
    }

    @Bean
    public HDFSStoreService hdfsStoreService() {
        return new HDFSStoreService();
    }

}
