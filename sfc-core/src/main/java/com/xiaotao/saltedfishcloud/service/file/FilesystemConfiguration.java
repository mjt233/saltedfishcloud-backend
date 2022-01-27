package com.xiaotao.saltedfishcloud.service.file;

import com.xiaotao.saltedfishcloud.dao.mybatis.FileDao;
import com.xiaotao.saltedfishcloud.service.file.impl.filesystem.DefaultFileSystem;
import com.xiaotao.saltedfishcloud.service.file.impl.filesystem.DefaultDiskFileSystem;
import com.xiaotao.saltedfishcloud.service.file.impl.store.LocalStoreServiceFactory;
import com.xiaotao.saltedfishcloud.service.node.NodeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.Resource;

@Configuration
@Slf4j
public class FilesystemConfiguration {

    @Resource
    private FileDao fileDao;

    @Resource
    private NodeService nodeService;

    @Resource
    private FileRecordService fileRecordService;

    @Bean
    @ConditionalOnMissingBean(StoreServiceFactory.class)
    public StoreServiceFactory storeServiceFactory() {
        final LocalStoreServiceFactory factory = new LocalStoreServiceFactory();
        log.info("[STORE]使用默认的存储服务：{}", factory.getClass().getName());
        return factory;
    }

    @Bean
    @ConditionalOnMissingBean(DiskFileSystemFactory.class)
    public DiskFileSystemFactory diskFileSystemFactory() {
        return new DefaultFileSystem(defaultDiskFileSystem());
    }

    @Bean
    @ConditionalOnMissingBean(DiskFileSystem.class)
    public DefaultDiskFileSystem defaultDiskFileSystem() {
        return new DefaultDiskFileSystem(storeServiceFactory(), fileDao, fileRecordService, nodeService);
    }
}
