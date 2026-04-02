package com.xiaotao.saltedfishcloud.utils;

import org.springframework.beans.BeanUtils;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class ObjectUtils {
    private ObjectUtils() {}

    /**
     * 浅拷贝对象
     * @param src 被拷贝对象
     * @return  拷贝结果
     */
    public static <T> T clone(T src, Supplier<T> target) {
        T t = target.get();
        BeanUtils.copyProperties(src, t);
        return t;
    }

    /**
     * 对集合内的每个元素进行浅拷贝
     * @param collection    待拷贝的集合
     * @param target    集合元素的构造函数
     * @return  拷贝结果
     */
    public static <E, T extends Collection<E>> List<E> cloneListElement(T collection, Supplier<E> target) {
        return collection.stream().map(e -> clone(e, target)).toList();
    }

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

}
