package com.xiaotao.saltedfishcloud.ext;

import com.xiaotao.saltedfishcloud.ext.store.HadoopStoreService;
import com.xiaotao.saltedfishcloud.ext.store.HadoopStoreServiceFactory;
import com.xiaotao.saltedfishcloud.service.file.StoreService;
import com.xiaotao.saltedfishcloud.service.file.StoreServiceFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class HadoopAutoConfigure {

    @Bean
    public StoreService hadoopStoreService() {
        return new HadoopStoreService();
    }

    @Bean
    public StoreServiceFactory hadoopStoreServiceFactory() {
        return new HadoopStoreServiceFactory();
    }

}
