package com.xiaotao.saltedfishcloud.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.util.List;
import java.util.Map;

public class MapperHolder {
    public static final ObjectMapper mapper = new ObjectMapper();
    public static final ObjectMapper snakeMapper = new ObjectMapper();

    static {
        initMapperConfigure(mapper);
        initMapperConfigure(snakeMapper);
        snakeMapper.setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE);
    }

    private static void initMapperConfigure(ObjectMapper mapper) {
        // 忽略未知属性
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS);

        // 序列化时将Long转为字符串
        SimpleModule simpleModule = new SimpleModule();
        // long -> String
        simpleModule.addSerializer(Long.TYPE, ToStringSerializer.instance);
        // Long -> String
        simpleModule.addSerializer(Long.class, ToStringSerializer.instance);
        mapper.registerModule(simpleModule);
    }

    public static <T> T parseJson(String json, Class<T> clazz) throws JsonProcessingException {
        return mapper.readValue(json, clazz);
    }

    public static <T> T parseSnakeJson(String json, Class<T> clazz) throws JsonProcessingException {
        return snakeMapper.readValue(json, clazz);
    }

    public static <T> List<T> parseSnakeJsonToList(String json, Class<T> elementType) throws JsonProcessingException {
        return snakeMapper.readValue(json, snakeMapper.getTypeFactory().constructCollectionType(List.class, elementType));
    }

    public static Map<String, Object> parseSnakeJsonToMap(String json) throws JsonProcessingException {
        return snakeMapper.readValue(json, snakeMapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class));
    }

    public static <T> List<T> parseJsonToList(String json, Class<T> elementType) throws JsonProcessingException {
        return mapper.readValue(json, mapper.getTypeFactory().constructCollectionType(List.class, elementType));
    }

    public static Map<String, Object> parseJsonToMap(String json) throws JsonProcessingException {
        return mapper.readValue(json, mapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class));
    }

    public static <K,V> Map<K, V> parseJsonToMap(String json, Class<K> keyClass, Class<V> valueClass) throws JsonProcessingException {
        return mapper.readValue(json, mapper.getTypeFactory().constructMapType(Map.class, keyClass, valueClass));
    }

    public static String toJson(Object val) throws JsonProcessingException {
        return mapper.writeValueAsString(val);
    }
}
