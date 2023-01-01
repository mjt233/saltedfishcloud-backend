package com.xiaotao.saltedfishcloud.service.file;

import com.xiaotao.saltedfishcloud.model.FileSystemStatus;
import com.xiaotao.saltedfishcloud.service.file.store.DirectRawStoreHandler;
import io.jsonwebtoken.lang.Assert;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * 默认的唯一存储服务，适用于使用基类为{@link AbstractRawStoreService}的存储服务创建。
 */
@Slf4j
public class DefaultUniqueStoreService extends AbstractUniqueStoreService {
    private final AbstractRawStoreService rawStoreService;
    public DefaultUniqueStoreService(DirectRawStoreHandler handler,
                                     FileResourceMd5Resolver md5Resolver,
                                     AbstractRawStoreService rawStoreService
    ) {
        super(handler, md5Resolver, rawStoreService);
        this.rawStoreService = rawStoreService;
    }

    @Override
    public boolean isMoveWithRecursion() {
        return false;
    }

    @Override
    public String getPublicRoot() {
        return rawStoreService.getPublicRoot();
    }

    @Override
    public String getStoreRoot() {
        return rawStoreService.getStoreRoot();
    }

    @Override
    public List<FileSystemStatus> getStatus() {
        return rawStoreService.getStatus();
    }
}
