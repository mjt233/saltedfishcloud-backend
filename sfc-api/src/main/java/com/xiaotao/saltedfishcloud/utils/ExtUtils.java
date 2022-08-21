package com.xiaotao.saltedfishcloud.utils;

import com.xiaotao.saltedfishcloud.annotations.ConfigProperties;
import com.xiaotao.saltedfishcloud.annotations.ConfigPropertiesEntity;
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


    /**
     * 从类加载器中读取加载并解析插件的配置节点信息
     * @param loader    类加载器
     * @param prefix    配置文件所在目录前缀
     * @return          配置节点组
     * @throws IOException  任何IO错误
     */
    public static List<ConfigNode> getPluginConfigNodeFromLoader(ClassLoader loader, String prefix) throws IOException {
        String file = prefix == null ? PLUGIN_CONFIG_PROPERTIES_FILE : StringUtils.appendPath(prefix, PLUGIN_CONFIG_PROPERTIES_FILE);
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
                .filter(e -> e.getInputType().equals("form"))
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

                        // 读取引用的类配置实体信息
                        ConfigPropertiesEntity entity = refClass.getAnnotation(ConfigPropertiesEntity.class);

                        // 读取所有声明的配置组信息
                        List<ConfigNode> groupList = Arrays.stream(entity.groups()).map(g -> {
                            ConfigNode node = new ConfigNode();
                            node.setName(g.id());
                            node.setTitle(g.name());
                            return node;
                        }).collect(Collectors.toList());
                        Map<String, ConfigNode> groupMap = groupList.stream().collect(Collectors.toMap(ConfigNode::getName, Function.identity()));

                        // 读取配置实体的各个字段信息，并按所属组进行分组后，设置到所属组下
                        Map<String, List<ConfigNode>> subGroupMap = Arrays.stream(refClass.getDeclaredFields())
                                .filter(f -> f.getAnnotation(ConfigProperties.class) != null)
                                .map(f -> {
                                    ConfigProperties p = f.getAnnotation(ConfigProperties.class);
                                    ConfigNode configNode = new ConfigNode();
                                    configNode.setDescribe(p.describe());
                                    configNode.setDefaultValue(p.defaultValue());
                                    configNode.setInputType(p.inputType());
                                    configNode.setTitle(p.value());
                                    configNode.setName(f.getName());
                                    configNode.setGroupId(p.group());
                                    configNode.setMask(p.isMask());
                                    return configNode;
                                }).collect(Collectors.groupingBy(ConfigNode::getGroupId));
                        subGroupMap.forEach((groupId, nodes) -> {
                            ConfigNode parent = groupMap.get(groupId);
                            if (parent == null) {
                                String member = nodes.stream().map(ConfigNode::getName).collect(Collectors.joining());
                                throw new RuntimeException("找不到配置组：【" + groupId + "】 组成员：" + member);
                            }
                            parent.setNodes(nodes);
                        });
                        e.setNodes(groupList);
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
