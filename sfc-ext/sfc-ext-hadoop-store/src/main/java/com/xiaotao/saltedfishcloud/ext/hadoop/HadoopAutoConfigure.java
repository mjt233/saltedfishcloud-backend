package com.xiaotao.saltedfishcloud.ext.hadoop;

import com.xiaotao.saltedfishcloud.ext.hadoop.store.HadoopStoreService;
import com.xiaotao.saltedfishcloud.ext.hadoop.store.HadoopStoreServiceFactory;
import com.xiaotao.saltedfishcloud.service.file.StoreService;
import com.xiaotao.saltedfishcloud.service.file.StoreServiceFactory;
import org.apache.hadoop.fs.FileSystem;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

@Configuration
public class HadoopAutoConfigure {
    @Autowired
    private HadoopStoreProperties properties;

    @Bean
    public StoreService hadoopStoreService() {
        return new HadoopStoreService();
    }

    @Bean
    public StoreServiceFactory hadoopStoreServiceFactory() {
        return new HadoopStoreServiceFactory();
    }

    public FileSystem hadoopFileSystem() throws IOException, URISyntaxException, InterruptedException {
        final org.apache.hadoop.conf.Configuration conf = new org.apache.hadoop.conf.Configuration();
        conf.set("fs.defaultFS", properties.getUrl());
        conf.set("hadoop.root.logger", "OFF");
        return FileSystem.get(new URI(properties.getUrl()), conf, properties.getUser());
    }

}
