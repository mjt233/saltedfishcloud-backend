package com.saltedfishcloud.ext.hadoop;

import com.saltedfishcloud.ext.hadoop.store.HDFSStoreHandler;
import com.saltedfishcloud.ext.hadoop.store.HDFSStoreService;
import com.saltedfishcloud.ext.hadoop.store.HDFSStoreServiceProvider;
import com.xiaotao.saltedfishcloud.service.file.FileResourceMd5Resolver;
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
    @Autowired
    private FileResourceMd5Resolver md5Resolver;

    @Bean
    public FileSystem hadoopFileSystem() throws IOException, URISyntaxException, InterruptedException {
        final org.apache.hadoop.conf.Configuration conf = new org.apache.hadoop.conf.Configuration();
        conf.set("fs.defaultFS", properties.getUrl());
        conf.set("hadoop.root.logger", "WARN");
        conf.set("fs.hdfs.impl", "org.apache.hadoop.hdfs.DistributedFileSystem");
        return FileSystem.get(new URI(properties.getUrl()), conf, properties.getUser());
    }

    @Bean
    public HDFSStoreServiceProvider hdfsStoreServiceFactory() {
        return new HDFSStoreServiceProvider();
    }

    @Bean
    public HDFSStoreService hdfsStoreService() throws InterruptedException, IOException, URISyntaxException {
        return new HDFSStoreService(new HDFSStoreHandler(hadoopFileSystem()), properties, md5Resolver);
    }

}
