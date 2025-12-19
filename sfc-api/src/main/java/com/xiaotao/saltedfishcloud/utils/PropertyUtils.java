package com.xiaotao.saltedfishcloud.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.xiaotao.saltedfishcloud.annotations.*;
import com.xiaotao.saltedfishcloud.constant.ConfigInputType;
import com.xiaotao.saltedfishcloud.model.ConfigNode;
import com.xiaotao.saltedfishcloud.model.SelectOption;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
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

    public static String getConfigName(ConfigPropertyEntity entity, ConfigPropertiesGroup group, ConfigProperty property, String fieldName) {
        String prefix = "".equals(entity.prefix()) ? null : entity.prefix();
        if (group != null && !"".equals(group.prefix())) {
            prefix = prefix == null ? group.prefix() : prefix + "." + group.prefix();
        }
        String name;
        if ("".equals(property.value())) {
            ConfigKeyNameStrategy nameStrategy = property.defaultKeyNameStrategy() == ConfigKeyNameStrategy.INHERIT ? entity.defaultKeyNameStrategy() : property.defaultKeyNameStrategy();
            name = switch (nameStrategy) {
                case KEBAB_CASE -> StringUtils.camelToKebab(fieldName);
                case CAMEL_CASE -> fieldName;
                case UNDER_SCORE_CASE -> StringUtils.camelToUnder(fieldName);
                default -> throw new IllegalArgumentException("未实现的策略:" + nameStrategy);
            };
        } else {
            name = property.value();
        }
        return prefix == null ? name : prefix + "." + name;
    }

    /**
     * 获取配置项名称
     * @param entity    配置实体注解
     * @param property  配置实体字段注解
     * @param fieldName 字段名
     */
    public static String getConfigName(ConfigPropertyEntity entity, ConfigProperty property, String fieldName) {
        return getConfigName(entity, null, property, fieldName);
    }

    /**
     * 从配置实体类中获取配置节点
     * @param refClass     要读取的配置类，需要使用{@link ConfigPropertyEntity ConfigPropertiesEntity} 标注类，并在各个字段中标注对应的{@link ConfigProperty ConfigProperties}
     * @return          key - 配置节点组名称，value - 节点组下的配置节点
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
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
            node.setConfigPropertiesGroup(g);
            return node;
        }).toList();
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
                .filter(field -> field.getAnnotation(ConfigProperty.class) != null)
                .map(field -> {
                    ConfigProperty p = field.getAnnotation(ConfigProperty.class);
                    ConfigNode configNode = new ConfigNode();
                    configNode.setIsRow(p.isRow());
                    configNode.setDescribe(p.describe());
                    configNode.setDefaultValue(p.defaultValue());
                    configNode.setInputType(p.inputType());
                    configNode.setTitle(p.title());
                    configNode.setTemplate(p.template());
                    configNode.setReadonly(p.readonly());

                    // 解析复杂子配置节点对象
                    if (p.typeRef() != Object.class || ConfigInputType.FORM.equals(p.inputType())) {
                        Map<String, ConfigNode> subEntityGroup = getConfigNodeFromEntityClass(p.typeRef() == Object.class ? field.getType() : p.typeRef());
                        configNode.setNodes(subEntityGroup.values().stream().toList());
                    }
                    if (!"{}".equals(p.templateParams())) {
                        try {
                            configNode.setParams(MapperHolder.parseJsonToMap(p.templateParams()));
                        } catch (JsonProcessingException e) { throw new IllegalArgumentException(e); }
                    }

                    // 解析下拉选项，对于未手动配置选项的枚举类型则直接使用枚举成员
                    ConfigSelectOption[] options = p.options();
                    if (options.length > 0) {
                        configNode.setOptions(
                                Arrays.stream(options)
                                        .map(o -> SelectOption.builder().title(o.title()).value(o.value()).build())
                                        .collect(Collectors.toList())
                        );
                    } else if (Enum.class.isAssignableFrom(field.getType())) {
                        configNode.setOptions(EnumSet.allOf((Class<Enum>) field.getType())
                                .stream()
                                .map(e -> SelectOption.builder().title(e.toString()).value(e.toString()).build())
                                .toList()
                        );
                    }

                    ConfigNode parentGroup = groupMap.get(p.group());
                    if (parentGroup == null) {
                        throw new RuntimeException("组成员字段：【" + field.getName() + "】 找不到配置组：【" + p.group() + "】 ");
                    }
                    configNode.setName(getConfigName(entity, parentGroup.getConfigPropertiesGroup(), p, field.getName()));
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

    @RequiredArgsConstructor
    @Getter
    @Builder
    public static class ConfigFieldMeta {
        private final String configName;
        private final String fieldName;
        private final Method method;
        private final Field field;
        private final Class<?> entityClass;
        private final ConfigPropertyEntity configPropertyEntity;
        private final ConfigPropertiesGroup configPropertiesGroup;
        private final ConfigProperty configProperty;
    }

    /**
     * 通过被{@link ConfigPropertyEntity}修饰的参数配置类的字段getter方法lambda表达式来获取对应字段的配置项名称及其元数据。<br>
     * @param func  配置类字段getter方法的lambda表达式。 e.g. SysLogRecord::getEnableLog
     * @return  配置key
     */
    public static <T, R> ConfigFieldMeta parseLambdaConfigNameMeta(SFunc<T, R> func) {
        try {
            ClassUtils.LambdaMetaData lambdaMetaData = ClassUtils.parseGetterLambdaMetaData(func);
            Class<?> propertyEntityClass = lambdaMetaData.entityClass();
            Field field = lambdaMetaData.field();
            String fieldName = lambdaMetaData.fieldName();
            Method method = lambdaMetaData.method();

            ConfigPropertyEntity propertyEntity = propertyEntityClass.getDeclaredAnnotation(ConfigPropertyEntity.class);
            if (propertyEntity == null) {
                throw new IllegalArgumentException("类 " + propertyEntityClass + " 缺少@ConfigPropertyEntity");
            }

            ConfigProperty configProperty = field.getAnnotation(ConfigProperty.class);
            if (configProperty == null) {
                throw new IllegalArgumentException("类 " + propertyEntityClass + " 的字段 " + fieldName + " 缺少@ConfigProperty注解");
            }
            ConfigPropertiesGroup configPropertiesGroup = Arrays.stream(propertyEntity.groups()).filter(g -> g.id().equals(configProperty.group()))
                    .findAny()
                    .orElseThrow(() -> new IllegalArgumentException("类 " + propertyEntityClass + " 的字段 " + fieldName + " 上找不到所属配置组" + configProperty.group()));

            String configName = getConfigName(propertyEntity, configPropertiesGroup, configProperty, fieldName);
            return ConfigFieldMeta.builder()
                    .configName(configName)
                    .method(method)
                    .fieldName(fieldName)
                    .field(field)
                    .entityClass(propertyEntityClass)
                    .configProperty(configProperty)
                    .configPropertiesGroup(configPropertiesGroup)
                    .configPropertyEntity(propertyEntity)
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("通过lambda解析配置项名称异常", e);
        }
    }

    /**
     * 通过被{@link ConfigPropertyEntity}修饰的参数配置类的字段getter方法lambda表达式来获取对应字段的配置key
     * @param func  配置类字段getter方法的lambda表达式。 e.g. SysLogRecord::getEnableLog
     * @return  配置key
     */
    public static <T,R> String parseLambdaConfigName(SFunc<T, R> func) {
        return parseLambdaConfigNameMeta(func).getConfigName();
    }
}
