package com.xiaotao.saltedfishcloud.orm.config.utils;

import com.xiaotao.saltedfishcloud.orm.config.annotation.ConfigEntity;
import com.xiaotao.saltedfishcloud.orm.config.annotation.ConfigKey;
import com.xiaotao.saltedfishcloud.orm.config.annotation.IgnoreConfigKey;
import com.xiaotao.saltedfishcloud.orm.config.entity.MethodInst;
import com.xiaotao.saltedfishcloud.orm.config.enums.EntityKeyType;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;
import java.util.stream.Collectors;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Slf4j
public class ConfigReflectUtils {

    @Data
    @AllArgsConstructor
    public static class ConfigBinding {
        private String key;
        private Object inst;
        private Method method;
    }
    /**
     * 通过setter方法名获取对应的小驼峰命名法的字段名
     * @param name  setter方法名
     * @return      小驼峰命名法的字段名
     */
    public static String getFieldNameByMethodName(String name) {
        if (!name.startsWith("set")) {
            throw new IllegalStateException(name + " 不合法");
        }
        return ((char)(name.charAt(3) + 32) + name.substring(4));
    }

    /**
     * 通过小驼峰命名法的字段名获取对应的setter方法名
     * @param name  小驼峰命名法的字段名
     * @return      setter方法名
     */
    public static String getSetterNameByFieldName(String name) {
        return "set" + ((char)(name.charAt(0) - 32) + name.substring(1));
    }

    /**
     * 通过小驼峰命名法的字段名获取对应的getter方法名
     * @param name  小驼峰命名法的字段名
     * @return      getter方法名
     */
    public static String getGetterNameByFieldName(String name) {
        return "get" + ((char)(name.charAt(0) - 32) + name.substring(1));
    }

    /**
     * 判断是否为有效的可作为配置参数的值的对象类型。
     * 有效的值类型：boolean、char、short、int、long、float、double、以及其包装类，String，BigInt，BigDecimal
     * @param clazz 待判断的值
     * @return  有效true，否则false
     */
    public static boolean isValidValueType(Class<?> clazz) {
        return Boolean.class.isAssignableFrom(clazz) ||
                Character.class.isAssignableFrom(clazz) ||
                Short.class.isAssignableFrom(clazz) ||
                Integer.class.isAssignableFrom(clazz) ||
                Long.class.isAssignableFrom(clazz) ||
                Float.class.isAssignableFrom(clazz) ||
                Double.class.isAssignableFrom(clazz) ||
                String.class.isAssignableFrom(clazz) ||
                BigInteger.class.isAssignableFrom(clazz) ||
                BigDecimal.class.isAssignableFrom(clazz);



    }

    /**
     * 获取对象中指定配置节点属性的方法实例
     * @param key   配置节点
     * @param obj   配置对象
     * @return      key为配置节点，value为setter方
     */
    public static MethodInst getMethodInst(String key, Object obj) throws NoSuchFieldException, NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
        final String[] nodes = key.split("\\.");
        if (nodes.length < 2) {
            throw new IllegalArgumentException("无效的配置节点：" + key);
        }
        Object curObj = obj;
        for (int i = 1; i < nodes.length; i++) {
            String node = nodes[i];

            if (i == nodes.length - 1) {
                final String setter = getSetterNameByFieldName(node);
                final String getter = getGetterNameByFieldName(node);
                final Method setterMethod = curObj.getClass().getDeclaredMethod(setter, curObj.getClass().getDeclaredField(node).getType());
                final Method getterMethod = curObj.getClass().getDeclaredMethod(getter);
                final Class<?> returnType = getterMethod.getReturnType();
                if (!isValidValueType(returnType)) {
                    throw new IllegalArgumentException("不支持的配置值类型：" + returnType);
                }
                return new MethodInst(
                            setterMethod,
                            getterMethod,
                            curObj
                    );
            }

            // 获取下一节点的配置实体对象
            final Method getterMethod = curObj.getClass().getDeclaredMethod(getGetterNameByFieldName(node));
            getterMethod.setAccessible(true);
            Object nextObj = getterMethod.invoke(curObj);

            // 获取不到则通过无参构造器构造后，再通过Setter方法设置进去
            if (nextObj == null) {
                nextObj = newInstance(getterMethod.getReturnType());
                invokeSetter(curObj, node, nextObj);
                log.debug("实例化配置子节点实体：" + nextObj.getClass());
            }

            curObj = nextObj;

            final ConfigEntity configEntity = curObj.getClass().getDeclaredAnnotation(ConfigEntity.class);
            if (configEntity == null || !node.equals(configEntity.value())) {
                throw new IllegalArgumentException("不存在的节点：" + key);
            }
        }
        throw new IllegalArgumentException("不存在的节点：" + key);
    }

    /**
     * 调用对象指定字段的setter方法
     * @param obj       待调用对象
     * @param field     setter方法对应的字段
     * @param val       设置的值
     * @return          setter方法的返回值
     */
    public static Object invokeSetter(Object obj, String field, Object val) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        final Method setter = obj.getClass().getDeclaredMethod(getSetterNameByFieldName(field), val.getClass());
        setter.setAccessible(true);
        return setter.invoke(obj, val);
    }

    /**
     * 通过无参构造器构造一个新对象
     * @param type  待构造的对象类型class
     * @param <T>   待构造类型
     * @return      构造完成的对象
     */
    public static <T> T newInstance(Class<T> type) throws NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
        final Constructor<T> constructor = type.getConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }

    /**
     * 获取对象中声明的所有配置节点
     * @param clazz   类
     * @return  配置节点名集合
     */
    public static List<String> getAllConfigKey(Class<?> clazz) {
        final ConfigEntity annotation = AnnotationUtils.findAnnotation(clazz, ConfigEntity.class);
        List<String> res = new ArrayList<>();

        // 先获取所有的字段
        final Map<String, Field> fieldMap = Arrays.stream(clazz.getDeclaredFields()).
                collect(
                        Collectors.toMap(
                                Field::getName,
                                e -> e,
                                (v1, v2) -> v1)

                );

        // 再获取所有的setter方法
        for (Method method : ReflectionUtils.getAllDeclaredMethods(clazz)) {
            if (!method.getName().startsWith("set") || method.getParameters().length != 1) {
                continue;
            }
            assert annotation != null;
            final String fieldName = ConfigReflectUtils.getFieldNameByMethodName(method.getName());
            final Field field = fieldMap.get(fieldName);


            // 以下情况不作为子节点：
            // 1. 存在@IgnoreConfigKey注解
            // 2. NOT_FULL类型下没有@ConfigKey
            // 3. setter方法没有对应字段

            if (
                    field == null ||
                    field.getDeclaredAnnotation(IgnoreConfigKey.class) != null ||
                    (annotation.keyType() == EntityKeyType.NOT_ALL && field.getDeclaredAnnotation(ConfigKey.class) == null)
            ) {
                continue;
            }

            final String key = annotation.value() + "." + fieldName;

            final Class<?> fieldType = field.getType();
            final ConfigEntity fieldAnnotation = fieldType.getDeclaredAnnotation(ConfigEntity.class);
            if (fieldAnnotation != null) {
                res.addAll(
                        getAllConfigKey(fieldType)
                                .stream()
                                .map(e -> annotation.value() + "." + e)
                                .collect(Collectors.toSet())
                );
            } else {
                res.add(key);
            }
        }
        return res;
    }

}
