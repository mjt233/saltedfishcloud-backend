package com.xiaotao.saltedfishcloud.service.file;

import com.xiaotao.saltedfishcloud.service.file.impl.store.LocalStoreServiceProvider;
import com.xiaotao.saltedfishcloud.service.file.impl.store.LocalStoreService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
@ConditionalOnProperty(prefix = "sys.store", name = "type", havingValue = "local")
public class LocalStoreAutoConfigure {
    @Autowired
    private FileResourceMd5Resolver md5Resolver;

    @Bean
    public StoreServiceProvider storeServiceFactory() {
        return new LocalStoreServiceProvider(rawStoreService());
    }

    @Bean
    public LocalStoreService rawStoreService() {
        return new LocalStoreService(md5Resolver);
    }
}
