package com.xiaotao.saltedfishcloud.ext;

import com.xiaotao.saltedfishcloud.exception.PluginNotFoundException;
import com.xiaotao.saltedfishcloud.model.ConfigNode;
import com.xiaotao.saltedfishcloud.model.PluginInfo;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * 拓展插件管理器
 */
public interface PluginManager {

    /**
     * 注册插件
     * @param pluginResource 插件jar包资源
     */
    void register(Resource pluginResource) throws IOException;

    /**
     * 单纯注册插件元数据
     * @param name                  插件名称
     * @param pluginInfo            插件信息
     * @param configNodeGroupList   提供的配置组
     * @param loader                插件的类加载器
     */
    void registerPluginMetaData(String name, PluginInfo pluginInfo, List<ConfigNode> configNodeGroupList, ClassLoader loader);

    /**
     * 获取拓展插件信息。若不存在则返回null
     * @param name  拓展插件名称
     * @return      插件信息
     */
    PluginInfo getPluginInfo(String name);

    /**
     * 获取所有注册的插件
     * @return  插件信息列表，key - 插件name，value - 插件信息
     */
    Map<String, PluginInfo> getAllPlugin();

    /**
     * 获取所有注册的插件
     */
    List<PluginInfo> listAllPlugin();

    /**
     * 获取插件的类加载器
     * @param name  插件名称
     */
    ClassLoader getPluginClassLoader(String name) throws PluginNotFoundException;

    /**
     * 获取插件的配置节点组
     * @param name  插件名称
     */
    List<ConfigNode> getPluginConfigNodeGroup(String name);

    /**
     * 获取合并了各个插件的类加载器
     */
    ClassLoader getJarMergeClassLoader();
}
