package com.sfc.ext.webdav.store;

import com.sfc.ext.webdav.store.filesystem.WebDavStorageFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WebDavStoreAutoConfiguration {
    @Bean
    public WebDavStorageFactory webDavStoreFileSystemFactory() {
        return new WebDavStorageFactory();
    }
}
