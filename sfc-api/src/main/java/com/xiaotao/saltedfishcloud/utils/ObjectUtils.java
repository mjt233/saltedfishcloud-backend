package com.xiaotao.saltedfishcloud.utils;

import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

import javax.persistence.Table;
import javax.persistence.Transient;
import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ObjectUtils {
    private ObjectUtils() {}
    public static void copyMapToBean(Map<?, ?> map, Object object) {
        try {
            Class<?> clazz = object.getClass();
            BeanInfo beanInfo = Introspector.getBeanInfo(clazz);
            PropertyDescriptor[] propertyDescriptors = beanInfo.getPropertyDescriptors();
            for (PropertyDescriptor property : propertyDescriptors) {
                Method setter = property.getWriteMethod();
                if (setter == null) {
                    Method getter = property.getReadMethod();
                    if (getter != null) {
                        try {
                            setter = clazz.getMethod("set" + StringUtils.camelToUpperCamel(property.getName()), getter.getReturnType());
                        } catch (NoSuchMethodException ignore) {
                            continue;
                        }
                    }
                }
                if (map.containsKey(property.getName()) && setter != null) {
                    Class<?> parameterType = setter.getParameterTypes()[0];
                    setter.invoke(object, TypeUtils.convert(parameterType, map.get(property.getName())));
                }
            }
        } catch (IntrospectionException | InvocationTargetException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
    /**
     * map转对象
     */
    public static <T> T mapToBean(Map<?, ?> map, Class<T> beanClass) {
        try {
            T object = beanClass.getDeclaredConstructor().newInstance();
            copyMapToBean(map, object);
            return object;
        } catch (InvocationTargetException | InstantiationException | IllegalAccessException | NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 获取实体类的物理数据表名
     */
    public static String getEntityTableName(Class<?> clazz) {
        Table tableAnnotation = clazz.getAnnotation(Table.class);
        if (tableAnnotation != null) {
            return tableAnnotation.name();
        }
        String simpleName = clazz.getSimpleName();
        int i = simpleName.indexOf("$");
        if (i >= 0) {
            simpleName = simpleName.substring(0, i);
        }
        return StringUtils.camelToUnder(simpleName);
    }

    /**
     * 获取类的数据表实体字段及其getter方法
     */
    public static List<Tuple2<String, Method>> getClassEntityFieldGetter(Class<?> clazz) {
        List<Field> fieldList = ClassUtils.getAllFields(clazz);
        return fieldList.stream()
                .filter(f -> f.getAnnotation(Transient.class) == null && !Modifier.isTransient(f.getModifiers()) && !Modifier.isStatic(f.getModifiers()) )
                .map(f -> {
                    try {
                        return Tuples.of(StringUtils.camelToUnder(f.getName()), clazz.getMethod("get" + StringUtils.camelToUpperCamel(f.getName())));
                    } catch (NoSuchMethodException e) {
                        throw new IllegalArgumentException(e);
                    }
                })
                .collect(Collectors.toList());
    }
}
