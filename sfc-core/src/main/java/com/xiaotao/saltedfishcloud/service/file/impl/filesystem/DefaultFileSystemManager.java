package com.xiaotao.saltedfishcloud.service.file.impl.filesystem;

import com.xiaotao.saltedfishcloud.common.SystemOverviewItemProvider;
import com.xiaotao.saltedfishcloud.exception.FileSystemParameterException;
import com.xiaotao.saltedfishcloud.exception.UnsupportedFileSystemProtocolException;
import com.xiaotao.saltedfishcloud.model.ConfigNode;
import com.xiaotao.saltedfishcloud.model.po.User;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystem;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystemFactory;
import com.xiaotao.saltedfishcloud.service.file.DiskFileSystemManager;
import com.xiaotao.saltedfishcloud.service.file.StoreServiceFactory;
import com.xiaotao.saltedfishcloud.utils.SecureUtils;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

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
        return Collections.singletonList(ConfigNode.builder()
                        .name("fileSystemFeature")
                        .title("文件系统功能")
                        .nodes(Arrays.asList(
                                ConfigNode.builder().name("mainFileSystem")
                                        .title("主存储服务")
                                        .value(storeServiceFactory.toString()).build(),
                                ConfigNode.builder().name("protocols")
                                        .title("支持的挂载协议")
                                        .value(protocols).build(),
                                ConfigNode.builder().name("publicProtocols")
                                        .title("开放的挂载协议")
                                        .value(StringUtils.hasText(publicProtocols) ? publicProtocols : "-").build()
                        ))
                .build());
    }
}
