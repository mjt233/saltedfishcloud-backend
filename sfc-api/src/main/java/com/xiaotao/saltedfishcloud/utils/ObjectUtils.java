package com.xiaotao.saltedfishcloud.utils;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;

public class ObjectUtils {
    private ObjectUtils() {}
    public static void copyMapToBean(Map<?, ?> map, Object object) {
        try {
            BeanInfo beanInfo = Introspector.getBeanInfo(object.getClass());
            PropertyDescriptor[] propertyDescriptors = beanInfo.getPropertyDescriptors();
            for (PropertyDescriptor property : propertyDescriptors) {
                Method setter = property.getWriteMethod();
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
