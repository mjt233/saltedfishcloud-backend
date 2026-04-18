package com.xiaotao.saltedfishcloud.service.file.impl.store;

import com.xiaotao.saltedfishcloud.config.SysProperties;
import com.xiaotao.saltedfishcloud.enums.StoreMode;
import com.xiaotao.saltedfishcloud.model.config.SysCommonConfig;
import com.xiaotao.saltedfishcloud.service.file.StoreService;
import com.xiaotao.saltedfishcloud.service.file.StoreServiceFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

@Slf4j
public class LocalStoreServiceFactory implements StoreServiceFactory {
    private final StoreService storeService;

    public LocalStoreServiceFactory(StoreService storeService) {
        this.storeService = storeService;
    }

    @Autowired
    private SysCommonConfig sysCommonConfig;

    @Override
    public StoreService getService() {
        StoreMode storeMode = sysCommonConfig.getStoreMode();
        if (storeMode == StoreMode.RAW) {
            return storeService.getRawStoreService();
        } else if (storeMode == StoreMode.UNIQUE) {
            return storeService.getUniqueStoreService();
        } else {
            throw new UnsupportedOperationException("不支持的存储类型：" + storeMode);
        }
    }
}
