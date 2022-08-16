package com.xiaotao.saltedfishcloud.ext;

import com.xiaotao.saltedfishcloud.model.PluginInfo;

import java.util.List;

/**
 * 插件服务接口
 */
public interface PluginService {

    /**
     * 获取注册的插件列表
     */
    List<PluginInfo> listPlugins();
}
