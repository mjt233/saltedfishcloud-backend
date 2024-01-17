package com.xiaotao.saltedfishcloud.utils;

import com.xiaotao.saltedfishcloud.helper.MapValidator;

import java.util.Collection;
import java.util.Map;

public class CollectionUtils {
    private CollectionUtils() {}

    /**
     * 校验map对象
     */
    public static <K,V> MapValidator<K,V> validMap(Map<K,V> map) {
        return new MapValidator<>(map);
    }

    public static <T> boolean isEmpty(Collection<T> collection) {
        return collection == null || collection.isEmpty();
    }
}
