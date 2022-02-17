package com.xiaotao.saltedfishcloud.service.file.impl.store;

import com.xiaotao.saltedfishcloud.config.SysProperties;
import com.xiaotao.saltedfishcloud.service.file.AbstractRawStoreService;
import com.xiaotao.saltedfishcloud.service.file.FileResourceMd5Resolver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

import java.io.IOException;

/**
 * 本地文件系统存储服务，网盘文件数据保存在本地文件系统当中
 */
@Slf4j
public class LocalStoreService extends AbstractRawStoreService implements ApplicationRunner {
    @Autowired
    private SysProperties sysProperties;

    private String storeRoot;
    private String publicRoot;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        storeRoot = sysProperties.getStore().getRoot();
        publicRoot = sysProperties.getStore().getPublicRoot();
    }

    public LocalStoreService(FileResourceMd5Resolver md5Resolver) {
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
        return publicRoot;
    }

    @Override
    public String getStoreRoot() {
        return storeRoot;
    }

    @Override
    public int delete(String md5) throws IOException {
        throw new UnsupportedOperationException();
    }

}
