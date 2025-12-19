package com.xiaotao.saltedfishcloud.utils;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.springframework.security.core.parameters.P;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

public class MapperHolder {
    public static final ObjectMapper mapper = new ObjectMapper();
    public static final ObjectMapper snakeMapper = new ObjectMapper();
    public static final ObjectMapper withTypeMapper = new ObjectMapper();
    private static final String CLASS_PREFIX = "{\"@class\":";
    private static final byte[] CLASS_PREFIX_BYTES = CLASS_PREFIX.getBytes(StandardCharsets.UTF_8);

    static {
        initMapperConfigure(mapper);
        initMapperConfigure(snakeMapper);
        initMapperConfigure(withTypeMapper);
        snakeMapper.setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE);

        withTypeMapper.activateDefaultTyping(withTypeMapper.getPolymorphicTypeValidator(), ObjectMapper.DefaultTyping.NON_FINAL, JsonTypeInfo.As.PROPERTY);
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
        mapper.setTypeFactory(mapper.getTypeFactory().withClassLoader(Thread.currentThread().getContextClassLoader()));
    }

    public static <T> T parseJson(String json, Class<T> clazz) throws IOException {
        return parseAsJson(json, clazz);
    }

    @SuppressWarnings("unchecked")
    public static <T> T parseAsJson(Object jsonObj, Class<T> clazz) throws IOException {
        if (jsonObj instanceof String && clazz == String.class) {
            return (T)mapper.readValue(jsonObj.toString(), String.class);
        }
        if (clazz.isAssignableFrom(jsonObj.getClass())) {
            return (T)jsonObj;
        } else if (jsonObj instanceof String) {
            return matchObjectMapper(jsonObj).readValue((String) jsonObj, clazz);
        } else if ("[B".equals(jsonObj.getClass().getName())) {
            return matchObjectMapper(jsonObj).readValue((byte[]) jsonObj, clazz);
        } else {
            return mapper.readValue(jsonObj.toString(), clazz);
        }
    }

    /**
     * 根据json是否带类型去匹配对应的ObjectMapper
     * @param jsonObj   待序列化的json数据
     * @return          mapper
     */
    private static ObjectMapper matchObjectMapper(Object jsonObj) {
        boolean withType = isWithTypeJson(jsonObj);
        if (withType) {
            return withTypeMapper;
        } else {
            return mapper;
        }
    }

    /**
     * 是否为包含了类型的json序列化字符串或字节
     * @param jsonObj   字符串或byte[]类型若为{"@class":开头，则表示为带类型的json
     * @return          是否为带类型的json
     */
    private static boolean isWithTypeJson(Object jsonObj) {
        if (jsonObj instanceof String) {
            return ((String) jsonObj).startsWith("{\"@class\":");
        } else if ("[B".equals(jsonObj.getClass().getName())) {
            boolean withType = true;
            byte[] bytes = (byte[]) jsonObj;
            for (int i = 0; i < CLASS_PREFIX_BYTES.length; i++) {
                if (bytes[i] != CLASS_PREFIX_BYTES[i]) {
                    withType = false;
                    break;
                }
            }
            return withType;
        } else {
            return false;
        }
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

    /**
     * 转为json字符串，不显式抛出异常，但会包装成RuntimeException
     */
    public static String toJsonNoEx(Object val) {
        try {
            return mapper.writeValueAsString(val);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

    }

    public static String toJsonWithType(Object val) throws JsonProcessingException {
        return withTypeMapper.writeValueAsString(val);
    }
}
