package com.saltedfishcloud.ext.samba.config;

import com.saltedfishcloud.ext.samba.filesystem.SambaDiskFileSystemFactory;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystemManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SambaFileSystemAutoConfiguration {
    @Autowired
    private DiskFileSystemManager diskFileSystemManager;

    @Bean
    public SambaDiskFileSystemFactory sambaDiskFileSystemFactory() {
        SambaDiskFileSystemFactory factory = new SambaDiskFileSystemFactory();
        diskFileSystemManager.registerFileSystem(factory);
        return factory;
    }
}
