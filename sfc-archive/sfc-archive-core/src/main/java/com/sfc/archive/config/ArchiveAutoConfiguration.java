package com.sfc.archive.config;

import com.sfc.archive.ArchiveManager;
import com.sfc.archive.ArchiveManagerImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ArchiveAutoConfiguration {
    @Bean
    public ArchiveManager archiveManager() {
        return new ArchiveManagerImpl();
    }
}
