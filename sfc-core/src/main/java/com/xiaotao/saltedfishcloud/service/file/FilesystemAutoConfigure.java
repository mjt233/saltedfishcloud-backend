package com.xiaotao.saltedfishcloud.service.file;

import com.xiaotao.saltedfishcloud.service.file.impl.filesystem.DefaultFileSystem;
import com.xiaotao.saltedfishcloud.service.file.impl.filesystem.DefaultFileSystemProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FilesystemAutoConfigure {

    @Bean
    public DiskFileSystemProvider diskFileSystemFactory() {
        return new DefaultFileSystemProvider(defaultFileSystem());
    }

    @Bean
    @ConditionalOnMissingBean(DiskFileSystem.class)
    public DefaultFileSystem defaultFileSystem() {
        return new DefaultFileSystem();
    }
}
