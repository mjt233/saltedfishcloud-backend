package com.xiaotao.saltedfishcloud.utils;

import com.xiaotao.saltedfishcloud.annotations.ConfigProperties;
import com.xiaotao.saltedfishcloud.annotations.ConfigPropertiesEntity;
import com.xiaotao.saltedfishcloud.model.ConfigNode;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class PropertyUtils {

    private static void dereferenceNodes(Collection<ConfigNode> nodes, ClassLoader classLoader, StringBuilder errMsg) {
        if (nodes == null) {
            return;
        }
        for (ConfigNode node : nodes) {
            try {
                if (StringUtils.hasText(node.getTypeRef())) {
                    Class<?> refClass;
                    // 先从插件的直接加载器类型引用
                    try {
                        refClass = classLoader.loadClass(node.getTypeRef());
                    } catch (ClassNotFoundException ex) {
                        refClass = Thread.currentThread().getContextClassLoader().loadClass(node.getTypeRef());
                    }

                    List<ConfigNode> configNodes = new ArrayList<>(PropertyUtils.getConfigNodeFromEntityClass(refClass).values());
                    node.setNodes(configNodes);
                }
                // 递归处理
                dereferenceNodes(node.getNodes(), classLoader, errMsg);
            } catch (ClassNotFoundException ex) {
                errMsg.append("找不到类型引用：").append(node.getTypeRef());
            }
        }
    }


    /**
     * 对节点配置的typeRef进行解引用，并赋值到nodes属性中。
     */
    public static void dereferenceNodes(Collection<ConfigNode> nodes, ClassLoader classLoader) {
        StringBuilder err = new StringBuilder();
        dereferenceNodes(nodes, classLoader, err);
        if (err.length() != 0) {
            throw new IllegalArgumentException(err.toString());
        }

    }

    /**
     * 对节点配置的typeRef进行解引用，并赋值到nodes属性中。
     */
    public static void dereferenceNodes(Collection<ConfigNode> nodes) {
        dereferenceNodes(nodes, Thread.currentThread().getContextClassLoader());
    }

    /**
     * 从配置实体类中获取配置节点
     * @param refClass     要读取的配置类，需要使用{@link com.xiaotao.saltedfishcloud.annotations.ConfigPropertiesEntity ConfigPropertiesEntity} 标注类，并在各个字段中标注对应的{@link com.xiaotao.saltedfishcloud.annotations.ConfigProperties ConfigProperties}
     * @return          key - 配置节点集名称，value - 节点集下的配置节点
     */
    public static Map<String, ConfigNode> getConfigNodeFromEntityClass(Class<?> refClass) {

        // 读取引用的类配置实体信息
        ConfigPropertiesEntity entity = refClass.getAnnotation(ConfigPropertiesEntity.class);
        if (entity == null) {
            throw new IllegalArgumentException(refClass + "上没有@ConfigPropertiesEntity注解");
        }
        String prefix = "".equals(entity.prefix()) ? null : entity.prefix();

        // 读取所有声明的配置组信息
        List<ConfigNode> groupList = Arrays.stream(entity.groups()).map(g -> {
            ConfigNode node = new ConfigNode();
            node.setName(g.id());
            node.setTitle(g.name());
            node.setDescribe(g.describe());
            return node;
        }).collect(Collectors.toList());
        Map<String, ConfigNode> groupMap = groupList
                .stream()
                .collect(Collectors.toMap(
                        ConfigNode::getName,
                        Function.identity(),
                        (oldVal, newVal) -> {throw new IllegalArgumentException("配置组name冲突：" + oldVal.getName());},
                        LinkedHashMap::new
                ));

        // 读取配置实体的各个字段信息，并按所属组进行分组后，设置到所属组下
        Map<String, List<ConfigNode>> subGroupMap = Arrays.stream(refClass.getDeclaredFields())
                .filter(f -> f.getAnnotation(ConfigProperties.class) != null)
                .map(f -> {
                    ConfigProperties p = f.getAnnotation(ConfigProperties.class);
                    ConfigNode configNode = new ConfigNode();
                    configNode.setDescribe(p.describe());
                    configNode.setDefaultValue(p.defaultValue());
                    configNode.setInputType(p.inputType());
                    configNode.setTitle(StringUtils.hasText(p.title()) ? p.title() : p.value() );

                    String name;
                    if ("".equals(p.value())) {
                        name = StringUtils.camelToKebab(f.getName());
                    } else {
                        name = p.value();
                    }
                    configNode.setName(prefix == null ? name : prefix + "." + name);
                    configNode.setGroupId(p.group());
                    configNode.setMask(p.isMask());
                    configNode.setRequired(p.required());
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
        return groupMap;
    }
}
