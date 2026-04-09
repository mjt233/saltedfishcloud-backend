package com.xiaotao.saltedfishcloud.utils;

import com.xiaotao.saltedfishcloud.helper.MapValidator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
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

    /**
     * 将集合按指定大小拆分成多个小的集合
     * @param collection 待拆分的集合
     * @param size 每个小集合的大小
     * @return 拆分后的集合列表
     */
    public static <T> List<List<T>> partition(Collection<T> collection, int size) {
        if (collection == null || size <= 0) {
            throw new IllegalArgumentException("collection不能为null且size必须大于0");
        }
        
        List<T> list = collection instanceof List ? (List<T>) collection : new ArrayList<>(collection);
        
        int totalSize = list.size();
        if (totalSize <= size) {
            return Collections.singletonList(list.subList(0, totalSize));
        }
        
        List<List<T>> result = new ArrayList<>((totalSize + size - 1) / size);
        
        for (int i = 0; i < totalSize; i += size) {
            int end = Math.min(i + size, totalSize);
            result.add(list.subList(i, end));
        }
        
        return result;
    }

}
