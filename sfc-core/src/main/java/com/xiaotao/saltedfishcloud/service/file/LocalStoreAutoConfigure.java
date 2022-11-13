package com.xiaotao.saltedfishcloud.service.file;

import com.xiaotao.saltedfishcloud.service.file.impl.store.LocalStoreService;
import com.xiaotao.saltedfishcloud.service.file.impl.store.LocalStoreServiceFactory;
import com.xiaotao.saltedfishcloud.service.hello.FeatureProvider;
import com.xiaotao.saltedfishcloud.service.hello.HelloService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
@ConditionalOnProperty(prefix = "sys.store", name = "type", havingValue = "local")
public class LocalStoreAutoConfigure implements FeatureProvider {
    @Autowired
    private FileResourceMd5Resolver md5Resolver;

    @Bean
    public StoreServiceFactory storeServiceFactory() {
        return new LocalStoreServiceFactory(localStoreService());
    }

    @Bean
    public LocalStoreService localStoreService() {
        return new LocalStoreService(md5Resolver);
    }


    @Override
    public void registerFeature(HelloService helloService) {
        helloService.appendFeatureDetail("fileSystem", "local");
    }

}
