package com.xiaotao.saltedfishcloud.service.file.impl.filesystem;

import com.xiaotao.saltedfishcloud.common.SystemOverviewItemProvider;
import com.xiaotao.saltedfishcloud.model.ConfigNode;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystem;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystemManager;
import com.xiaotao.saltedfishcloud.service.file.StorageFactory;
import com.xiaotao.saltedfishcloud.service.file.StorageRegistry;
import com.xiaotao.saltedfishcloud.model.config.SysCommonConfig;
import com.xiaotao.saltedfishcloud.service.file.StoreServiceFactory;
import com.xiaotao.saltedfishcloud.utils.PathUtils;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

@Component
@Slf4j
public class DefaultFileSystemManager implements DiskFileSystemManager, SystemOverviewItemProvider {
    @Autowired
    private StoreServiceFactory storeServiceFactory;

    @Autowired
    private SysCommonConfig sysCommonConfig;

    /**
     * 存储工厂注册表。
     */
    @Autowired
    private StorageRegistry storageRegistry;


    @Autowired
    private DiskFileSystemDispatcher dispatcher;


    @Override
    public DiskFileSystem getMainFileSystem() {
        return dispatcher;
    }

    @Override
    public void setMainFileSystem(DiskFileSystem fileSystem) {
        dispatcher.setMainFileSystem(fileSystem);
    }

    @Override
    public List<ConfigNode> provideItem(Map<String, ConfigNode> existItem) {
        List<StorageFactory> storageFactories = storageRegistry.listStorageFactory();
        String protocols = storageFactories.stream().map(e -> e.getMetadata().getProtocol()).collect(Collectors.joining(","));
        String publicProtocols = storageRegistry.listPublicStorageFactory().stream().map(e -> e.getMetadata().getProtocol()).collect(Collectors.joining(","));
        String tempSize = StringUtils.getFormatSize(new File(PathUtils.getTempDirectory()).getFreeSpace());
        return Collections.singletonList(ConfigNode.builder()
                        .name("fileSystemFeature")
                        .title("文件系统功能")
                        .nodes(Arrays.asList(
                                new ConfigNode("存储模式", sysCommonConfig.getStoreMode()),
                                new ConfigNode("系统临时目录", PathUtils.getTempDirectory()),
                                new ConfigNode("临时目录可用空间", tempSize),
                                new ConfigNode("主存储服务", storeServiceFactory.toString()),
                                new ConfigNode("支持的挂载协议", protocols),
                                new ConfigNode("开放的挂载协议", StringUtils.hasText(publicProtocols) ? publicProtocols : "-")
                        ))
                .build());
    }
}
