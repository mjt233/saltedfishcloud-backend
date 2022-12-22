package com.xiaotao.saltedfishcloud.common.update;

import com.xiaotao.saltedfishcloud.service.config.version.Version;

/**
 * 版本迭代操作器，用于执行版本升级迭代操作以便进行版本滚动更新，处理旧数据
 * 若有VersionUpdateHandler作为Bean注册到了Spring Context，则会被自动注册到更新管理器VersionUpdateManager中。
 */
public interface VersionUpdateHandler {

    /**
     * 获取更新消息，在执行更新时会被打印出来
     */
    default String getMessage() {
        return "";
    }

    /**
     * 执行版本更新操作
     * @param from      旧版本号
     * @param to        新版本号
     * @throws Exception    任意异常抛出以中断更新流程
     */
    void update(Version from, Version to) throws Exception;

    /**
     * 更新流程失败时的回滚操作。一次流程更新的执行是一序列更新操作器的执行，任意一环更新出错都会触发该方法的执行以便进行操作回滚。
     */
    default void rollback(Version from, Version to) {

    }

    /**
     * 获取更新器的作用域，一般使用插件唯一标识，用作仅针对插件本身的内容更新。
     * @return 插件的name，若返回null则表示不是插件而是全局作用域。
     */
    default String getScope() {
        return null;
    }

    /**
     * 需要该控制器执行所截止的版本号，系统版本高于或等于该版本时不会执行该操作器。
     * 如果返回null，则表示该方法为初始化方法，在目标组件第一次被系统加载时调用
     */
    Version getUpdateVersion();
}
