package com.xiaotao.saltedfishcloud.service.file;

import com.xiaotao.saltedfishcloud.dao.mybatis.FileDao;
import com.xiaotao.saltedfishcloud.ext.hadoop.store.HadoopStoreServiceFactory;
import com.xiaotao.saltedfishcloud.service.file.impl.filesystem.DiskFileSystemFactoryImpl;
import com.xiaotao.saltedfishcloud.service.file.impl.filesystem.LocalDiskFileSystem;
import com.xiaotao.saltedfishcloud.service.file.impl.store.HardLinkStoreService;
import com.xiaotao.saltedfishcloud.service.file.impl.store.LocalStoreConfig;
import com.xiaotao.saltedfishcloud.service.file.impl.store.RAWStoreService;
import com.xiaotao.saltedfishcloud.service.file.impl.store.StoreServiceFactoryImpl;
import com.xiaotao.saltedfishcloud.service.file.impl.store.path.RawPathHandler;
import com.xiaotao.saltedfishcloud.service.node.NodeService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.Resource;

@Configuration
public class FilesystemConfiguration {

//    @Resource
//    @Qualifier("RAWStoreService")
//    private RAWStoreService rawStoreService;
//
//    @Resource
//    private HardLinkStoreService hardLinkStoreService;

    @Resource
    private RawPathHandler pathHandler;

    @Resource
    private FileDao fileDao;

    @Resource
    private NodeService nodeService;

    @Resource
    private FileRecordService fileRecordService;

    @Bean
    @ConditionalOnMissingBean(StoreServiceFactory.class)
    public StoreServiceFactory storeServiceFactory() {
        return new HadoopStoreServiceFactory();
    }

    @Bean
    @ConditionalOnMissingBean(DiskFileSystemFactory.class)
    public DiskFileSystemFactory diskFileSystemFactory() {
        return new DiskFileSystemFactoryImpl(localDiskFileSystem());
    }

    @Bean
    @ConditionalOnMissingBean(DiskFileSystem.class)
    public LocalDiskFileSystem localDiskFileSystem() {
        return new LocalDiskFileSystem(storeServiceFactory(), fileDao, fileRecordService, nodeService, pathHandler);
    }
}
