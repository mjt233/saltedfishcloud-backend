package com.xiaotao.saltedfishcloud.common.update;

import com.xiaotao.saltedfishcloud.service.config.version.Version;

import java.util.List;

/**
 * 版本更新管理器
 */
public interface VersionUpdateManager {
    /**
     * 注册一个更新操作器
     */
    void registerUpdateHandler(VersionUpdateHandler handler);

    /**
     * 获取已注册的更新操作器列表
     */
    List<VersionUpdateHandler> getAllUpdateHandlerList();

    /**
     * 获取需要执行更新的操作器，也就是操作器的更新版本要比传入的版本号大。
     * @param scope 作用域，null表示全局作用域，或使用插件的name表示插件自身的更新操作
     * @param referVersion  参考版本
     * @return      有序操作器列表，按更新版本升序排序
     */
    List<VersionUpdateHandler> getNeedUpdateHandlerList(String scope, Version referVersion);


}
