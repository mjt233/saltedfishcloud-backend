package com.xiaotao.saltedfishcloud.utils;

import com.xiaotao.saltedfishcloud.ext.PluginDependenceConflictException;
import com.xiaotao.saltedfishcloud.ext.PluginInfoException;
import com.xiaotao.saltedfishcloud.ext.PluginManager;
import com.xiaotao.saltedfishcloud.model.ConfigNode;
import com.xiaotao.saltedfishcloud.model.PluginInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StreamUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class ExtUtils {
    private static final String EXTENSION_DIRECTORY = "ext";

    /**
     * 获取拓展目录绝对路径
     */
    public static String getExtensionDirectory() {
        return new File(EXTENSION_DIRECTORY).getAbsolutePath();
    }

    public static PluginInfo getPluginInfo(ClassLoader loader, String prefix) throws IOException {
        String file = prefix == null ? PluginManager.PLUGIN_INFO_FILE : StringUtils.appendPath(prefix, PluginManager.PLUGIN_INFO_FILE);
        String infoJson = ExtUtils.getResourceText(loader, file);
        if (!StringUtils.hasText(infoJson)) {
            throw new IllegalArgumentException("plugin-info.json为空");
        }
        PluginInfo pluginInfo = MapperHolder.parseJson(infoJson, PluginInfo.class);
        if (pluginInfo == null) {
            return null;
        }

        URL url = loader.getResource(file);
        if (url != null) {
            pluginInfo.setUrl(url.toString().replaceAll("(!/)?" + file, "").replaceAll("^jar:", ""));
        }
        return pluginInfo;
    }

    /**
     * 从类加载器中读取资源文件并返回该文件的文本内容
     * @param loader    类加载器
     * @param name      类资源名称
     */
    public static String getResourceText(ClassLoader loader, String name) throws IOException {
        try(InputStream is = loader.getResourceAsStream(name)){
            if (is != null) {
                return StreamUtils.copyToString(is, StandardCharsets.UTF_8);
            }
        }
        Enumeration<URL> resources = loader.getResources(name);
        if (resources.hasMoreElements()) {
            URL url = resources.nextElement();
            try( InputStream is = url.openStream()){
                return StreamUtils.copyToString(is, StandardCharsets.UTF_8);
            }
        } else {
            return "";
        }
    }

    /**
     * 从类加载器中读取加载并解析插件的配置节点信息
     * @param loader    类加载器
     * @param prefix    配置文件所在目录前缀
     * @return          配置节点组
     * @throws IOException  任何IO错误
     */
    public static List<ConfigNode> getPluginConfigNodeFromLoader(ClassLoader loader, String prefix) throws IOException {
        String file = prefix == null ? PluginManager.CONFIG_PROPERTIES_FILE : StringUtils.appendPath(prefix, PluginManager.CONFIG_PROPERTIES_FILE);
        String json = ExtUtils.getResourceText(loader, file);
        if (!StringUtils.hasText(json)) {
            return Collections.emptyList();
        }
        List<ConfigNode> configNodeGroups = MapperHolder.parseJsonToList(json, ConfigNode.class);
        PropertyUtils.dereferenceNodes(configNodeGroups, loader);
        return configNodeGroups;
    }

    /**
     * 获取插件jar包目录
     */
    public static Path getExtDir() {
        return Paths.get(EXTENSION_DIRECTORY);
    }

    /**
     * 获取拓展模块的URL数组
     * @return 拓展模块的URL
     */
    public static URL[] getExtUrls() {
        final File root = new File(EXTENSION_DIRECTORY);
        if (root.exists()) {
            if (root.isFile()) {
                log.warn("拓展目录路径{}为文件而不是目录！！", root);
                return new URL[0];
            }

            final File[] files = root.listFiles();
            if (files != null) {
                return Arrays.stream(files).filter(e -> e.getName().endsWith(".jar")).map(e -> {
                    try {
                        return e.toURI().toURL();
                    } catch (MalformedURLException ex) {
                        ex.printStackTrace();
                        return null;
                    }
                }).filter(Objects::nonNull).toArray(URL[]::new);
            } else {
                return new URL[0];
            }
        } else {
            return new URL[0];
        }
    }
}
