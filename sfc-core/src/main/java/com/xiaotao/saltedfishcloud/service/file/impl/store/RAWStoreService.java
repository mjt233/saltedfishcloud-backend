package com.xiaotao.saltedfishcloud.service.file.impl.store;

import com.xiaotao.saltedfishcloud.service.file.AbstractRawStoreService;
import com.xiaotao.saltedfishcloud.service.file.FileResourceMd5Resolver;
import com.xiaotao.saltedfishcloud.utils.DiskFileUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;

@Slf4j
public class RAWStoreService extends AbstractRawStoreService {
    @Autowired
    private LocalStoreConfig localStoreConfig;

    public RAWStoreService(FileResourceMd5Resolver md5Resolver) {
        super(new LocalDirectRawStoreHandler(), md5Resolver);
    }

    @Override
    public boolean canBrowse() {
        return true;
    }

    @Override
    public boolean isMoveWithRecursion() {
        return false;
    }

    @Override
    public String getPublicRoot() {
        return LocalStoreConfig.PUBLIC_ROOT;
    }

    @Override
    public String getStoreRoot() {
        return LocalStoreConfig.STORE_ROOT;
    }

    @Override
    public int delete(String md5) throws IOException {
        throw new UnsupportedOperationException();
    }

}
