package com.xiaotao.saltedfishcloud.service.file;

import com.xiaotao.saltedfishcloud.dao.mybatis.FileDao;
import com.xiaotao.saltedfishcloud.service.file.impl.filesystem.DefaultFileSystem;
import com.xiaotao.saltedfishcloud.service.file.impl.filesystem.DefaultFileSystemProvider;
import com.xiaotao.saltedfishcloud.service.node.NodeService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.Resource;

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
