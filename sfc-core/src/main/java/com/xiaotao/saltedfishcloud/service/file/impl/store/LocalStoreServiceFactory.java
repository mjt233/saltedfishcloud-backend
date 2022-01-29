package com.xiaotao.saltedfishcloud.service.file.impl.store;

import com.xiaotao.saltedfishcloud.config.StoreType;
import com.xiaotao.saltedfishcloud.service.file.StoreService;
import com.xiaotao.saltedfishcloud.service.file.StoreServiceFactory;
import org.springframework.beans.factory.annotation.Autowired;

public class LocalStoreServiceFactory implements StoreServiceFactory {
    @Autowired
    private RAWStoreService rawLocalStoreService;

    @Autowired
    private HardLinkStoreService hardLinkLocalStoreService;

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
