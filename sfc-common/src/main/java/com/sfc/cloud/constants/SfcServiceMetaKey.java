package com.sfc.cloud.constants;

public interface SfcServiceMetaKey {
    /**
     * 用于在服务注册时声明当前服务的微服务插件信息获取端点（endpoint）<br>
     * 值通常为"/"开头，如："/api/meta/pluginInfo"
     * 注册服务时若声明了 sfc.plugin.info，则会注册作为 sfc-core 的插件一同向前端暴露
     */
    String PLUGIN_INFO = "sfc.plugin.info";

    /**
     * 声明作为插件时需要自动加载的资源端点（endpoint），多个值使用英文半角逗号","隔开<br>
     */
    String PLUGIN_AUTOLOAD = "sfc.plugin.autoload";
}
