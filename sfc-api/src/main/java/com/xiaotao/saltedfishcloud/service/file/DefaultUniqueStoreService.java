package com.xiaotao.saltedfishcloud.service.file;

import com.xiaotao.saltedfishcloud.service.file.store.DirectRawStoreHandler;
import lombok.extern.slf4j.Slf4j;

/**
 * 默认的唯一存储服务，适用于使用基类为{@link AbstractRawStoreService}的存储服务创建。
 */
@Slf4j
public class DefaultUniqueStoreService extends AbstractUniqueStoreService {
    private final String publicRoot;
    private final String storeRoot;
    public DefaultUniqueStoreService(DirectRawStoreHandler handler,
                                     FileResourceMd5Resolver md5Resolver,
                                     AbstractRawStoreService rawStoreService,
                                     String publicRoot,
                                     String storeRoot
    ) {
        super(handler, md5Resolver, rawStoreService);
        this.publicRoot = publicRoot;
        this.storeRoot = storeRoot;
    }

    @Override
    public boolean isMoveWithRecursion() {
        return false;
    }

    @Override
    public String getPublicRoot() {
        return publicRoot;
    }

    @Override
    public String getStoreRoot() {
        return storeRoot;
    }
}
