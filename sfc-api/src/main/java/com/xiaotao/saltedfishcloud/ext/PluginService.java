package com.xiaotao.saltedfishcloud.ext;

import com.xiaotao.saltedfishcloud.exception.PluginNotFoundException;
import com.xiaotao.saltedfishcloud.model.PluginInfo;
import org.springframework.core.io.Resource;

import java.util.List;

/**
 * 插件服务接口
 */
public interface PluginService {

    /**
     * 获取注册的插件列表
     */
    List<PluginInfo> listPlugins();

    /**
     * 获取插件的静态文件资源
     * @param name  插件名称
     * @param path  静态资源路径（相对于的插件静态资源目录的路径）
     */
    Resource getPluginStaticResource(String name, String path) throws PluginNotFoundException;
}
