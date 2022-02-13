package com.saltedfishcloud.ext.hadoop.store;

import com.saltedfishcloud.ext.hadoop.HDFSProperties;
import com.xiaotao.saltedfishcloud.service.file.AbstractRawStoreService;
import com.xiaotao.saltedfishcloud.service.file.FileResourceMd5Resolver;
import com.xiaotao.saltedfishcloud.service.file.store.DirectRawStoreHandler;

public class HDFSStoreService extends AbstractRawStoreService {

    private final HDFSProperties properties;

    public HDFSStoreService(DirectRawStoreHandler handler, HDFSProperties properties, FileResourceMd5Resolver md5Resolver) {
        super(handler, md5Resolver);
        this.properties = properties;
    }

    @Override
    public boolean isMoveWithRecursion() {
        return false;
    }

    @Override
    public String getPublicRoot() {
        return properties.getStoreRoot(0);
    }

    @Override
    public String getStoreRoot() {
        return properties.getRoot();
    }

    @Override
    public boolean canBrowse() {
        return true;
    }
}
