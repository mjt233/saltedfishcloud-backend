package com.xiaotao.saltedfishcloud.service.file.impl.store;

import com.xiaotao.saltedfishcloud.config.StoreType;
import com.xiaotao.saltedfishcloud.service.file.StoreService;
import com.xiaotao.saltedfishcloud.service.file.StoreServiceFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

public class StoreServiceFactoryImpl implements StoreServiceFactory {
    private final RAWStoreService rawLocalStoreService;
    private final HardLinkStoreService hardLinkLocalStoreService;

    public StoreServiceFactoryImpl(@Qualifier("RAWStoreService") RAWStoreService rawLocalStoreService,
                                   HardLinkStoreService hardLinkLocalStoreService) {
        this.rawLocalStoreService = rawLocalStoreService;
        this.hardLinkLocalStoreService = hardLinkLocalStoreService;
    }

    @Override
    public StoreService getService() {
        if (LocalStoreConfig.STORE_TYPE == StoreType.RAW) {
            return rawLocalStoreService;
        } else if (LocalStoreConfig.STORE_TYPE == StoreType.UNIQUE) {
            return hardLinkLocalStoreService;
        } else {
            throw new UnsupportedOperationException("不支持的存储类型：" + LocalStoreConfig.STORE_TYPE);
        }
    }
}
