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
     * 注册插件的资源数据
     * @param name                  插件名称
     * @param pluginInfo            插件信息
     * @param configNodeGroupList   提供的配置组
     * @param loader                插件的类加载器
     */
    void registerPluginResource(String name, PluginInfo pluginInfo, List<ConfigNode> configNodeGroupList, ClassLoader loader);

    /**
     * 注册插件的资源数据
     * @param name                  插件名称
     * @param pluginInfo            插件信息
     * @param configNodeGroupList   提供的配置组
     * @param resourceRoot          限定插件的资源文件相对于插件jar包内容ClassPath的根路径，当获取插件的资源时，将会从该路径下获取
     * @param loader                插件的类加载器
     */
    void registerPluginResource(String name, PluginInfo pluginInfo, List<ConfigNode> configNodeGroupList, String resourceRoot,ClassLoader loader);

    /**
     * 获取插件的资源文件
     * @param name  插件名称
     * @param path  资源路径，如：static/aaa.js
     * @return      对应插件资源
     */
    Resource getPluginResource(String name, String path) throws PluginNotFoundException;

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
