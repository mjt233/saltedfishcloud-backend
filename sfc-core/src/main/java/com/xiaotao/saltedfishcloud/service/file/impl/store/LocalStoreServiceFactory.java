package com.xiaotao.saltedfishcloud.service.file.impl.store;

import com.xiaotao.saltedfishcloud.config.SysProperties;
import com.xiaotao.saltedfishcloud.enums.StoreMode;
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
    private SysProperties sysProperties;

    @Override
    public StoreService getService() {

        if (sysProperties.getStore().getMode() == StoreMode.RAW) {
            return storeService.getRawStoreService();
        } else if (sysProperties.getStore().getMode() == StoreMode.UNIQUE) {
            return storeService.getUniqueStoreService();
        } else {
            throw new UnsupportedOperationException("不支持的存储类型：" + sysProperties.getStore().getMode());
        }
    }
}
