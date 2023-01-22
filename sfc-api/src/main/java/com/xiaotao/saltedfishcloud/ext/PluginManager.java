package com.xiaotao.saltedfishcloud.ext;

import com.xiaotao.saltedfishcloud.exception.PluginNotFoundException;
import com.xiaotao.saltedfishcloud.model.ConfigNode;
import com.xiaotao.saltedfishcloud.model.PluginInfo;
import org.springframework.core.io.Resource;

import java.io.Closeable;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Map;

/**
 * 拓展插件管理器
 */
public interface PluginManager extends Closeable {
    String PLUGIN_INFO_FILE = "plugin-info.json";
    String CONFIG_PROPERTIES_FILE = "config-properties.json";

    /**
     * 移除插件
     */
    void remove(String name);

    /**
     * 安装插件
     * @param resource 插件资源
     */
    void installPlugin(Resource resource) throws IOException;

    /**
     * 对待升级的插件进行升级
     */
    void upgrade() throws IOException;

    /**
     * 解析插件信息
     * @param url   插件url
     */
    PluginInfo parsePlugin(URL url) throws IOException;


    /**
     * 注册插件
     * @param pluginResource 插件jar包资源
     */
    void register(Resource pluginResource) throws IOException;

    /**
     * 注册插件
     * @param pluginResource    插件类路径资源
     * @param classLoader       插件的类加载器
     */
    void register(Resource pluginResource, ClassLoader classLoader) throws IOException;

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
     * 获取所有可被识别的插件列表（包括未加载、已加载和待删除的）
     */
    List<PluginInfo> listAvailablePlugins() throws IOException;

    /**
     * 获取待删除的插件名称
     */
    List<String> listDeletePlugin();

    /**
     * 标记插件为待删除
     */
    void markPluginDelete(String name) throws IOException;

    /**
     * 删除所有未加载的被标记的插件
     */
    void deletePlugin() throws IOException;

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



//    /**
//     * 从插件库或目录中动态加载插件
//     * @param name  插件的资源名称（在ext目录下的jar包名称，如sfc-ext-demo.jar，那么就传入sfc-ext-demo）
//     */
//    void loadPlugin(String name) throws IOException;
}
