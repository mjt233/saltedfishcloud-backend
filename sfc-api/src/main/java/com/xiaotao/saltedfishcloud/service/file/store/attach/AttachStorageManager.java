package com.xiaotao.saltedfishcloud.service.file.store.attach;

import java.util.List;

/**
 * 附属数据存储服务管理器
 */
public interface AttachStorageManager {

    /**
     * 获取附属存储
     * @param storageDomainId   存储域id
     */
    AttachStorage getStorage(String storageDomainId);

    /**
     * 注册存储域
     * @param definition    存储域定义
     */
    void registerStorageDomain(AttachStorageDomainDefinition definition);

    /**
     * 移除存储域
     * @param storageDomainId   存储域id
     */
    void removeStorageDomain(String storageDomainId);

    /**
     * 列出已注册的存储域配置
     *
     * @return 只读列表
     */
    List<AttachStorageDomainDefinition> listStorageDomain();
}
