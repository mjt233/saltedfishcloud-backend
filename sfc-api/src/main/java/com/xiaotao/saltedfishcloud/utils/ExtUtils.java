package com.xiaotao.saltedfishcloud.utils;

import com.xiaotao.saltedfishcloud.annotations.ConfigProperties;
import com.xiaotao.saltedfishcloud.annotations.ConfigPropertiesEntity;
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
import java.util.*;
import java.util.function.Function;
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
        return MapperHolder.parseJson(infoJson, PluginInfo.class);
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
        List<ConfigNode> allNodes = configNodeGroups.stream().flatMap(e -> e.getNodes().stream()).flatMap(e -> e.getNodes().stream()).collect(Collectors.toList());
        StringBuilder errMsg = new StringBuilder();
        // 默认值缺失检测
        errMsg.append(
            allNodes
                .stream()
                .filter(e -> e.getDefaultValue() == null)
                .map(e -> "配置项【" + e.getName() + "】缺少默认值;")
                .collect(Collectors.joining())
        );

        // form类型的参数对象属性组装
        allNodes.stream()
                .filter(e -> e.getTypeRef() != null || e.getInputType().equals("form"))
                .forEach(e -> {
                    try {
                        if (!StringUtils.hasText(e.getTypeRef())) {
                            errMsg.append("配置节点【").append(e.getName()).append("】缺少类型引用typeRef;");
                            return;
                        }
                        Class<?> refClass = null;

                        // 先从插件的直接加载器类型引用
                        try {
                            refClass = loader.loadClass(e.getTypeRef());
                        } catch (ClassNotFoundException ex) {
                            // 找不到则使用默认的加载器加载（类型引用未在插件jar包中声明而是引用系统核心的类）
                            refClass = ExtUtils.class.getClassLoader().loadClass(e.getTypeRef());
                        }

                        e.setNodes(new ArrayList<>(PropertyUtils.getConfigNodeFromEntityClass(refClass).values()));
                    } catch (ClassNotFoundException ex) {
                        errMsg.append("找不到类型引用：").append(e.getTypeRef());
                    }
                });


        if (errMsg.length() > 0) {
            throw new RuntimeException(errMsg.toString());
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
