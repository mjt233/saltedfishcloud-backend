package com.saltedfishcloud.ext.minio;

import com.xiaotao.saltedfishcloud.service.file.AbstractRawStoreService;
import com.xiaotao.saltedfishcloud.service.file.FileResourceMd5Resolver;
import com.xiaotao.saltedfishcloud.service.file.store.DirectRawStoreHandler;

public class MinioStoreService extends AbstractRawStoreService {
    public MinioStoreService(DirectRawStoreHandler handler, FileResourceMd5Resolver md5Resolver) {
        super(handler, md5Resolver);
    }

    @Override
    public boolean isMoveWithRecursion() {
        return false;
    }

    @Override
    public String getPublicRoot() {
        return "";
    }

    @Override
    public String getStoreRoot() {
        return "";
    }
}
