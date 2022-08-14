package com.xiaotao.saltedfishcloud.ext;

import com.xiaotao.saltedfishcloud.enums.PluginLoadType;
import com.xiaotao.saltedfishcloud.exception.PluginNotFoundException;
import com.xiaotao.saltedfishcloud.model.ConfigNodeGroup;
import com.xiaotao.saltedfishcloud.model.PluginInfo;
import com.xiaotao.saltedfishcloud.utils.MapperHolder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 默认的插件管理器
 */
@Slf4j
public class DefaultPluginManager implements PluginManager {
    private final Map<String, PluginInfo> pluginMap = new ConcurrentHashMap<>();
    private final Map<String, PluginInfo> pluginMapView = Collections.unmodifiableMap(pluginMap);
    private final Map<String, ClassLoader> pluginClassLoaderMap = new ConcurrentHashMap<>();
    private final Map<String, List<ConfigNodeGroup>> pluginConfigNodeGroupMap = new ConcurrentHashMap<>();
    private final PluginClassLoader jarMergeClassLoader;

    @Getter
    private final ClassLoader masterLoader;

    public static final String PLUGIN_CONFIG_PROPERTIES_FILE = "config-properties.json";
    public static final String PLUGIN_INFO_FILE = "plugin-info.json";

    public DefaultPluginManager(ClassLoader masterLoader) {
        this.masterLoader = masterLoader;
        this.jarMergeClassLoader = new JarMergePluginClassLoader(masterLoader);
    }



    @Override
    public void register(Resource pluginResource) throws IOException {
        URL pluginUrl = pluginResource.getURL();
        URLClassLoader rawClassLoader = new URLClassLoader(new URL[]{pluginUrl});

        PluginInfo pluginInfo;
        List<ConfigNodeGroup> configNodeGroups;

        try {
            pluginInfo  = getPluginInfoFromLoader(rawClassLoader);
            configNodeGroups = getPluginConfigNodeFromLoader(rawClassLoader);
        } catch (IOException e) {
            log.error("获取插件信息失败：{}", pluginUrl);
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
        pluginMap.put(pluginInfo.getName(), pluginInfo);
        pluginConfigNodeGroupMap.put(pluginInfo.getName(), configNodeGroups);
        pluginClassLoaderMap.put(pluginInfo.getName(), loader);

    }

    protected List<ConfigNodeGroup> getPluginConfigNodeFromLoader(ClassLoader loader) throws IOException {
        try(InputStream configStream = loader.getResourceAsStream(PLUGIN_CONFIG_PROPERTIES_FILE)) {
            String json = StreamUtils.copyToString(configStream, StandardCharsets.UTF_8);
            if (!StringUtils.hasText(json)) {
                return Collections.emptyList();
            }
            List<ConfigNodeGroup> configNodeGroups = MapperHolder.parseJsonToList(json, ConfigNodeGroup.class);
            String errMsg = configNodeGroups.stream()
                    .flatMap(e -> e.getNodes().stream())
                    .filter(e -> e.getDefaultValue() == null)
                    .map(e -> "配置项【" + e.getName() + "】缺少默认值;")
                    .collect(Collectors.joining());

            if (errMsg.length() > 0) {
                throw new RuntimeException(errMsg);
            }
            return configNodeGroups;
        }
    }

    protected PluginInfo getPluginInfoFromLoader(ClassLoader loader) throws IOException {
        try(InputStream infoStream = loader.getResourceAsStream(PLUGIN_INFO_FILE)){
            String infoJson = StreamUtils.copyToString(infoStream, StandardCharsets.UTF_8);
            return MapperHolder.parseJson(infoJson, PluginInfo.class);
        }
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
