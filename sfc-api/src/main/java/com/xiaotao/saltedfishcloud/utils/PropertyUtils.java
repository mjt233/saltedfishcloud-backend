package com.xiaotao.saltedfishcloud.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.xiaotao.saltedfishcloud.annotations.ConfigProperty;
import com.xiaotao.saltedfishcloud.annotations.ConfigPropertyEntity;
import com.xiaotao.saltedfishcloud.annotations.ConfigSelectOption;
import com.xiaotao.saltedfishcloud.model.ConfigNode;
import com.xiaotao.saltedfishcloud.model.SelectOption;

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
     * 获取配置项名称
     * @param entity    配置实体注解
     * @param property  配置实体字段注解
     * @param fieldName 字段名
     */
    public static String getConfigName(ConfigPropertyEntity entity, ConfigProperty property, String fieldName) {
        String prefix = "".equals(entity.prefix()) ? null : entity.prefix();
        String name;
        if ("".equals(property.value())) {
            name = StringUtils.camelToKebab(fieldName);
        } else {
            name = property.value();
        }
        return prefix == null ? name : prefix + "." + name;
    }

    /**
     * 从配置实体类中获取配置节点
     * @param refClass     要读取的配置类，需要使用{@link ConfigPropertyEntity ConfigPropertiesEntity} 标注类，并在各个字段中标注对应的{@link ConfigProperty ConfigProperties}
     * @return          key - 配置节点组名称，value - 节点组下的配置节点
     */
    public static Map<String, ConfigNode> getConfigNodeFromEntityClass(Class<?> refClass) {

        // 读取引用的类配置实体信息
        ConfigPropertyEntity entity = refClass.getAnnotation(ConfigPropertyEntity.class);
        if (entity == null) {
            throw new IllegalArgumentException(refClass + "上没有@ConfigPropertiesEntity注解");
        }

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
                .filter(f -> f.getAnnotation(ConfigProperty.class) != null)
                .map(f -> {
                    ConfigProperty p = f.getAnnotation(ConfigProperty.class);
                    ConfigNode configNode = new ConfigNode();
                    configNode.setIsRow(p.isRow());
                    configNode.setDescribe(p.describe());
                    configNode.setDefaultValue(p.defaultValue());
                    configNode.setInputType(p.inputType());
                    configNode.setTitle(StringUtils.hasText(p.title()) ? p.title() : p.value() );
                    configNode.setTemplate(p.template());
                    if (!"{}".equals(p.templateParams())) {
                        try {
                            configNode.setParams(MapperHolder.parseJsonToMap(p.templateParams()));
                        } catch (JsonProcessingException e) { throw new IllegalArgumentException(e); }
                    }
                    ConfigSelectOption[] options = p.options();
                    if (options.length > 0) {
                        configNode.setOptions(
                                Arrays.stream(options)
                                        .map(o -> SelectOption.builder().title(o.title()).value(o.value()).build())
                                        .collect(Collectors.toList())
                        );
                    }

                    configNode.setName(getConfigName(entity, p, f.getName()));
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
