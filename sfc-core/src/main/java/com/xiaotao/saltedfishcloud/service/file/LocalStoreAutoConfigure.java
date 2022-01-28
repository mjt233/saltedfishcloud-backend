package com.xiaotao.saltedfishcloud.service.file;

import com.xiaotao.saltedfishcloud.service.file.impl.store.HardLinkStoreService;
import com.xiaotao.saltedfishcloud.service.file.impl.store.LocalStoreServiceFactory;
import com.xiaotao.saltedfishcloud.service.file.impl.store.RAWStoreService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "sys.store", name = "type", havingValue = "local")
public class LocalStoreAutoConfigure {

    @Bean
    public StoreServiceFactory storeServiceFactory() {
        return new LocalStoreServiceFactory();
    }

    @Bean
    @Qualifier("RAWStoreService")
    public RAWStoreService rawStoreService() {
        return new RAWStoreService();
    }

    @Bean
    @Qualifier("HardLinkStoreService")
    public HardLinkStoreService hardLinkStoreService() {
        return new HardLinkStoreService();
    }
}
