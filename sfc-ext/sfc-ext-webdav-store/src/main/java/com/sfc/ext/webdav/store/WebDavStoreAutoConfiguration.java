package com.sfc.ext.webdav.store;

import com.sfc.ext.webdav.store.filesystem.WebDavStoreFileSystemFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WebDavStoreAutoConfiguration {
    @Bean
    public WebDavStoreFileSystemFactory webDavStoreFileSystemFactory() {
        return new WebDavStoreFileSystemFactory();
    }
}
