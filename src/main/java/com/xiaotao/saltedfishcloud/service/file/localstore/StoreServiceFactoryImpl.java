package com.xiaotao.saltedfishcloud.service.file.localstore;

import com.xiaotao.saltedfishcloud.config.DiskConfig;
import com.xiaotao.saltedfishcloud.config.StoreType;
import org.springframework.stereotype.Component;

@Component
public class StoreServiceFactoryImpl implements StoreServiceFactory {
    private final RAWStoreService rawLocalStoreService;
    private final HardLinkStoreService hardLinkLocalStoreService;

    public StoreServiceFactoryImpl(RAWStoreService rawLocalStoreService, HardLinkStoreService hardLinkLocalStoreService) {
        this.rawLocalStoreService = rawLocalStoreService;
        this.hardLinkLocalStoreService = hardLinkLocalStoreService;
    }

    @Override
    public StoreService getService() {
        if (DiskConfig.STORE_TYPE == StoreType.RAW) {
            return rawLocalStoreService;
        } else if (DiskConfig.STORE_TYPE == StoreType.UNIQUE) {
            return hardLinkLocalStoreService;
        } else {
            throw new UnsupportedOperationException("不支持的存储类型：" + DiskConfig.STORE_TYPE);
        }
    }
}
