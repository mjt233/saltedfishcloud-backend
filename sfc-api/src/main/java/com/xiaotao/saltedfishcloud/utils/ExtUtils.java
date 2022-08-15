package com.xiaotao.saltedfishcloud.utils;

import com.xiaotao.saltedfishcloud.model.ConfigNodeGroup;
import com.xiaotao.saltedfishcloud.model.PluginInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.ResourceUtils;
import org.springframework.util.StreamUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class ExtUtils {
    private static final String EXTENSION_DIRECTORY = "ext";
    public static final String PLUGIN_CONFIG_PROPERTIES_FILE = "config-properties.json";
    public static final String PLUGIN_INFO_FILE = "plugin-info.json";

    /**
     * 获取拓展目录绝对路径
     */
    public static String getExtensionDirectory() {
        return new File(EXTENSION_DIRECTORY).getAbsolutePath();
    }

    public static PluginInfo getPluginInfo(ClassLoader loader, String prefix) throws IOException {
        String file = prefix == null ? PLUGIN_INFO_FILE : StringUtils.appendPath(prefix, PLUGIN_INFO_FILE);
        String infoJson = ExtUtils.getResourceText(loader, file);
        return MapperHolder.parseJson(infoJson, PluginInfo.class);
    }

    public static String getResourceText(ClassLoader loader, String name) throws IOException {
        try(InputStream is = loader.getResourceAsStream(name)){
            if (is != null) {
                String s = StreamUtils.copyToString(is, StandardCharsets.UTF_8);
                return s;
            }
        }
        Enumeration<URL> resources = loader.getResources(name);
        if (resources.hasMoreElements()) {
            URL url = resources.nextElement();
            try( InputStream is = url.openStream()){
                String s = StreamUtils.copyToString(is, StandardCharsets.UTF_8);
                log.debug("通过getResources获取数据：{} -> {}", name, s);
                return s;
            }
        } else {
            return "";
        }
    }


    public static List<ConfigNodeGroup> getPluginConfigNodeFromLoader(ClassLoader loader, String prefix) throws IOException {
        String file = prefix == null ? PLUGIN_CONFIG_PROPERTIES_FILE : StringUtils.appendPath(prefix, PLUGIN_CONFIG_PROPERTIES_FILE);
        String json = ExtUtils.getResourceText(loader, file);
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
