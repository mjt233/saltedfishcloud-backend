package com.xiaotao.saltedfishcloud.service.file;

import com.xiaotao.saltedfishcloud.dao.mybatis.FileDao;
import com.xiaotao.saltedfishcloud.service.file.impl.filesystem.DefaultFileSystemFactory;
import com.xiaotao.saltedfishcloud.service.file.impl.filesystem.DefaultFileSystem;
import com.xiaotao.saltedfishcloud.service.node.NodeService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.Resource;

@Configuration

public class FilesystemAutoConfigure {

    @Resource
    private FileDao fileDao;

    @Resource
    private NodeService nodeService;

    @Resource
    private FileRecordService fileRecordService;

    @Resource
    private StoreServiceFactory storeServiceFactory;


    @Bean
    public DiskFileSystemFactory diskFileSystemFactory() {
        return new DefaultFileSystemFactory(defaultFileSystem());
    }

    @Bean
    @ConditionalOnMissingBean(DiskFileSystem.class)
    public DefaultFileSystem defaultFileSystem() {
        return new DefaultFileSystem(storeServiceFactory, fileDao, fileRecordService, nodeService);
    }
}