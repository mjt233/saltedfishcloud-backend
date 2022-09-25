package com.xiaotao.saltedfishcloud.service.file;

/**
 * 网盘存储服务提供器，所有需要存储服务进行操作时，都应通过该接口获取存储服务来完成一系列操作。
 * 注意，不要缓存返回值用于下一次的一系列操作。因为可能存在多个存储服务，StoreServiceProvider能提供运行时存储服务选择能力。
 */
public interface StoreServiceFactory {
    StoreService getService();

    default TempStoreService getTempStoreService() {
        return getService().getTempFileHandler();
    }
}
