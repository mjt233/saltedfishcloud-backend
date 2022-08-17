package com.xiaotao.saltedfishcloud.ext;

import com.xiaotao.saltedfishcloud.enums.PluginLoadType;
import com.xiaotao.saltedfishcloud.exception.PluginNotFoundException;
import com.xiaotao.saltedfishcloud.model.ConfigNodeGroup;
import com.xiaotao.saltedfishcloud.model.PluginInfo;
import com.xiaotao.saltedfishcloud.utils.ExtUtils;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * 默认的插件管理器
 */
@Slf4j
public class DefaultPluginManager implements PluginManager {
    private final List<PluginInfo> pluginList = new CopyOnWriteArrayList<>();
    private final Map<String, PluginInfo> pluginMap = new ConcurrentHashMap<>();
    private final Map<String, PluginInfo> pluginMapView = Collections.unmodifiableMap(pluginMap);
    private final Map<String, ClassLoader> pluginClassLoaderMap = new ConcurrentHashMap<>();
    private final Map<String, List<ConfigNodeGroup>> pluginConfigNodeGroupMap = new ConcurrentHashMap<>();
    private final PluginClassLoader jarMergeClassLoader;

    @Getter
    private final ClassLoader masterLoader;


    public DefaultPluginManager(ClassLoader masterLoader) {
        this.masterLoader = masterLoader;
        this.jarMergeClassLoader = new JarMergePluginClassLoader(masterLoader);
    }



    @Override
    public void register(Resource pluginResource) throws IOException {
        URL pluginUrl = pluginResource.getURL();
        URLClassLoader rawClassLoader = new URLClassLoader(new URL[]{pluginUrl}, null);
        PluginInfo pluginInfo;
        List<ConfigNodeGroup> configNodeGroups;

        try {
            pluginInfo  = getPluginInfoFromLoader(rawClassLoader);
            configNodeGroups = getPluginConfigNodeFromLoader(rawClassLoader);
        } catch (Exception e) {
            log.error("获取插件信息失败，请检查插件的plugin-info.json：{}", pluginUrl);
            throw e;
        } finally {
            rawClassLoader.close();
        }


        PluginClassLoader loader;
        if (pluginInfo.getLoadType() == PluginLoadType.MERGE) {
            loader = jarMergeClassLoader;
        } else {
            throw new IllegalArgumentException("不支持的拓展加载方式：" + pluginInfo.getLoadType());
        }

        loader.loadFromUrl(pluginUrl);
        registerPluginMetaData(pluginInfo.getName(), pluginInfo, configNodeGroups, loader);

    }

    protected List<ConfigNodeGroup> getPluginConfigNodeFromLoader(ClassLoader loader) throws IOException {
        return ExtUtils.getPluginConfigNodeFromLoader(loader, null);
    }

    protected PluginInfo getPluginInfoFromLoader(ClassLoader rawLoader) throws IOException {
        return ExtUtils.getPluginInfo(rawLoader, null);
    }

    @Override
    public void registerPluginMetaData(String name, PluginInfo pluginInfo, List<ConfigNodeGroup> configNodeGroupList, ClassLoader loader) {
        pluginMap.put(pluginInfo.getName(), pluginInfo);
        pluginConfigNodeGroupMap.put(pluginInfo.getName(), configNodeGroupList);
        pluginClassLoaderMap.put(pluginInfo.getName(), loader);
        pluginList.add(pluginInfo);
    }

    @Override
    public PluginInfo getPluginInfo(String name) {
        return pluginMapView.get(name);
    }

    @Override
    public Map<String, PluginInfo> getAllPlugin() {
        return pluginMapView;
    }

    @Override
    public List<PluginInfo> listAllPlugin() {
        return pluginList;
    }

    @Override
    public ClassLoader getPluginClassLoader(String name) throws PluginNotFoundException {
        if (!pluginClassLoaderMap.containsKey(name)){
            throw new PluginNotFoundException(name);
        }

        return pluginClassLoaderMap.get(name);
    }

    @Override
    public ClassLoader getJarMergeClassLoader() {
        return jarMergeClassLoader;
    }

    @Override
    public List<ConfigNodeGroup> getPluginConfigNodeGroup(String name) {
        return pluginConfigNodeGroupMap.get(name);
    }
}
