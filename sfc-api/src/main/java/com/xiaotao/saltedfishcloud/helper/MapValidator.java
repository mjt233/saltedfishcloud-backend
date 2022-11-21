package com.xiaotao.saltedfishcloud.helper;

import com.xiaotao.saltedfishcloud.model.NameValueType;
import com.xiaotao.saltedfishcloud.utils.ObjectUtils;
import com.xiaotao.saltedfishcloud.utils.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MapValidator<K,V> {
    private final Map<K,V> map;
    private final Map<K, String> validMap = new HashMap<>();
    private boolean validString;
    public MapValidator(Map<K,V> map) {
        this.map = map;
    }

    /**
     * 是否校验空或无效字符串
     */
    public MapValidator<K,V> validString(boolean validString) {
        this.validString = validString;
        return this;
    }

    /**
     * 添加校验信息字段
     * @param key       待校验的key
     * @param name      该key的含义
     */
    public MapValidator<K,V> addField(K key, String name) {
        validMap.put(key, name);
        return this;
    }

    /**
     * 添加校验信息字段
     * @param key       待校验的key
     */
    public MapValidator<K,V> addField(K key) {
        validMap.put(key, key.toString());
        return this;
    }

    /**
     * 执行校验，校验不通过将抛出IllegalArgumentException
     * @throws IllegalArgumentException 校验不通过
     */
    public void valid() {
        List<String> invalidPropertyList = new ArrayList<>();
        validMap.forEach((key, name) -> {
            if(!map.containsKey(key)) {
                invalidPropertyList.add(name);
            } else {
                V v = map.get(key);
                if (validString && v instanceof String && !StringUtils.hasText((String) v)) {
                    invalidPropertyList.add(name);
                }
            }
        });
        if (!invalidPropertyList.isEmpty()) {
            throw new IllegalArgumentException("【" + String.join(",", invalidPropertyList) + "】不能为空");
        }
    }

    /**
     * 执行校验，并将属性复制到bean对象中
     */
    public void validAndCopyToBean(Object obj) {
        valid();
        ObjectUtils.copyMapToBean(map, obj);
    }

    /**
     * 执行校验，并将map转为bean
     * @param clazz     待转换的bean class
     */
    public <T> T validAndToBean(Class<T> clazz) {
        valid();
        return ObjectUtils.mapToBean(map, clazz);
    }
}
