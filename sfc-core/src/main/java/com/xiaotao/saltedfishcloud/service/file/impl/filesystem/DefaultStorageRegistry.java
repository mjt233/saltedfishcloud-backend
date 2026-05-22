package com.xiaotao.saltedfishcloud.service.file.impl.filesystem;

import com.xiaotao.saltedfishcloud.exception.FileSystemParameterException;
import com.xiaotao.saltedfishcloud.exception.UnsupportedFileSystemProtocolException;
import com.xiaotao.saltedfishcloud.model.po.UserPrincipal;
import com.xiaotao.saltedfishcloud.service.file.StorageFactory;
import com.xiaotao.saltedfishcloud.service.file.StorageRegistry;
import com.xiaotao.saltedfishcloud.service.file.store.Storage;
import com.xiaotao.saltedfishcloud.utils.SecureUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 默认存储工厂注册表实现。
 */
@Component
@Slf4j
public class DefaultStorageRegistry implements StorageRegistry {
    /**
     * 日志前缀。
     */
    private static final String LOG_PREFIX = "[StorageRegistry]";

    /**
     * 记录协议与存储工厂的映射关系。
     */
    private final Map<String, StorageFactory> factoryMap = new ConcurrentHashMap<>();

    /**
     * 注入并注册系统中的全部存储工厂。
     *
     * @param factories Spring 容器中的存储工厂列表
     */
    @Autowired(required = false)
    public void setFactories(List<StorageFactory> factories) {
        if (factories != null) {
            factories.forEach(this::registerStorageFactory);
        }
    }

    @Override
    public void registerStorageFactory(StorageFactory factory) {
        StorageFactory existFactory = factoryMap.get(factory.getMetadata().getProtocol().toLowerCase());
        if (existFactory != null) {
            throw new IllegalArgumentException(factory.getMetadata().getProtocol() + "协议的文件系统已经被注册：" + factory.getMetadata());
        } else {
            log.info("{}为{}协议注册文件系统：{}", LOG_PREFIX, factory.getMetadata().getProtocol(), factory.getMetadata().getName());
            factoryMap.put(factory.getMetadata().getProtocol(), factory);
        }
    }

    @Override
    public List<StorageFactory> listPublicStorageFactory() {
        UserPrincipal user = SecureUtils.getSpringSecurityUser();
        if (user != null && user.isAdmin()) {
            return new ArrayList<>(factoryMap.values());
        } else {
            return factoryMap.values().stream().filter(e -> e.getMetadata().isPublic()).collect(Collectors.toList());
        }
    }

    @Override
    public List<StorageFactory> listStorageFactory() {
        return List.copyOf(factoryMap.values());
    }

    @Override
    public Storage getStorage(String protocol, Map<String, Object> params) throws FileSystemParameterException {
        StorageFactory factory = factoryMap.get(protocol);
        if (factory == null) {
            throw new UnsupportedFileSystemProtocolException(protocol);
        }
        return factory.getStorage(params);
    }

    @Override
    public StorageFactory getStorageFactory(String protocol) {
        return factoryMap.get(protocol.toLowerCase());
    }

    @Override
    public boolean isSupportedProtocol(String protocol) {
        return factoryMap.containsKey(protocol.toLowerCase());
    }
}

