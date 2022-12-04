package com.xiaotao.saltedfishcloud.service.file.impl.filesystem;

import com.xiaotao.saltedfishcloud.common.SystemOverviewItemProvider;
import com.xiaotao.saltedfishcloud.config.SysProperties;
import com.xiaotao.saltedfishcloud.exception.FileSystemParameterException;
import com.xiaotao.saltedfishcloud.exception.UnsupportedFileSystemProtocolException;
import com.xiaotao.saltedfishcloud.model.ConfigNode;
import com.xiaotao.saltedfishcloud.model.po.User;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystem;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystemFactory;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystemManager;
import com.xiaotao.saltedfishcloud.service.file.StoreServiceFactory;
import com.xiaotao.saltedfishcloud.utils.PathUtils;
import com.xiaotao.saltedfishcloud.utils.SecureUtils;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Component
@Slf4j
public class DefaultFileSystemManager implements DiskFileSystemManager, SystemOverviewItemProvider {
    private final static String LOG_PREFIX = "[DiskFileManager]";
    /**
     * 记录各个文件系统对应的所支持的协议
     */
    private final Map<String, DiskFileSystemFactory> factoryMap = new ConcurrentHashMap<>();

    @Autowired
    @Lazy
    private StoreServiceFactory storeServiceFactory;

    @Autowired
    private SysProperties sysProperties;


    @Autowired
    private DiskFileSystemDispatcher dispatcher;


    @Override
    public List<DiskFileSystemFactory> listAllFileSystem() {
        return List.copyOf(factoryMap.values());
    }


    @Override
    public DiskFileSystem getMainFileSystem() {
        return dispatcher;
    }

    @Override
    public void setMainFileSystem(DiskFileSystem fileSystem) {
        dispatcher.setMainFileSystem(fileSystem);
    }

    @Override
    public void registerFileSystem(DiskFileSystemFactory factory) {
        DiskFileSystemFactory existFactory = factoryMap.get(factory.getDescribe().getProtocol().toLowerCase());
        if (existFactory != null) {
            throw new IllegalArgumentException(factory.getDescribe().getProtocol() + "协议的文件系统已经被注册：" + factory.getDescribe());
        } else {
            log.info("{}为{}协议注册文件系统：{}", LOG_PREFIX, factory.getDescribe().getProtocol(), factory.getDescribe().getName());
            factoryMap.put(factory.getDescribe().getProtocol(), factory);
        }
    }


    @Override
    public DiskFileSystem getFileSystem(String protocol, Map<String, Object> params) throws FileSystemParameterException {
        DiskFileSystemFactory factory = factoryMap.get(protocol);
        if (factory == null) {
            throw new UnsupportedFileSystemProtocolException(protocol);
        }
        return factory.getFileSystem(params);
    }

    @Override
    public DiskFileSystemFactory getFileSystemFactory(String protocol) {
        return factoryMap.get(protocol.toLowerCase());
    }

    @Override
    public boolean isSupportedProtocol(String protocol) {
        return factoryMap.containsKey(protocol.toLowerCase());
    }

    @Override
    public List<DiskFileSystemFactory> listPublicFileSystem() {
        User user = SecureUtils.getSpringSecurityUser();
        if (user != null && user.isAdmin()) {
            return new ArrayList<>(factoryMap.values());
        } else {
            return factoryMap.values().stream().filter(e -> e.getDescribe().isPublic()).collect(Collectors.toList());
        }
    }

    @Override
    public List<ConfigNode> provideItem(Map<String, ConfigNode> existItem) {
        String protocols = factoryMap.values().stream().map(e -> e.getDescribe().getProtocol()).collect(Collectors.joining(","));
        String publicProtocols = factoryMap.values().stream().filter(e -> e.getDescribe().isPublic()).map(e -> e.getDescribe().getProtocol()).collect(Collectors.joining(","));
        String tempSize = StringUtils.getFormatSize(new File(PathUtils.getTempDirectory()).getFreeSpace());
        return Collections.singletonList(ConfigNode.builder()
                        .name("fileSystemFeature")
                        .title("文件系统功能")
                        .nodes(Arrays.asList(
                                new ConfigNode("存储模式", sysProperties.getStore().getMode().toString()),
                                new ConfigNode("系统临时目录", PathUtils.getTempDirectory()),
                                new ConfigNode("临时目录可用空间", tempSize),
                                new ConfigNode("主存储服务", storeServiceFactory.toString()),
                                new ConfigNode("支持的挂载协议", protocols),
                                new ConfigNode("开放的挂载协议", StringUtils.hasText(publicProtocols) ? publicProtocols : "-")
                        ))
                .build());
    }
}
