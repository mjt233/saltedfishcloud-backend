package com.xiaotao.saltedfishcloud.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.util.List;
import java.util.Map;

public class MapperHolder {
    public static final ObjectMapper mapper = new ObjectMapper();
    static {
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

    public static <T> List<T> parseJsonToList(String json, Class<T> elementType) throws JsonProcessingException {
        return mapper.readValue(json, mapper.getTypeFactory().constructCollectionType(List.class, elementType));
    }

    public static Map<String, Object> parseJsonToMap(String json) throws JsonProcessingException {
        return mapper.readValue(json, mapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class));
    }
}
