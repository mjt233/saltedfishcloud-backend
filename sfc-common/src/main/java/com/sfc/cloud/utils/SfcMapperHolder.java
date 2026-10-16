package com.sfc.cloud.utils;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 共享的 Jackson {@link ObjectMapper} 持有者。
 *
 * <p>由宿主 {@code com.xiaotao.saltedfishcloud.utils.MapperHolder} 整类逐字迁移而来，
 * 逻辑、字段与方法签名一字未改，仅包名/类名变更。该类自包含，唯一第三方依赖是 jackson。</p>
 *
 * <p><b>对外契约：</b>{@code com.sfc.cloud.model.json.SfcJsonResult#getJsonStr()} 直接依赖
 * {@link #mapper} 这一 public static 字段，因此该字段的名称与可见性不可改动。</p>
 */
public class SfcMapperHolder {
    /** 默认的 ObjectMapper：忽略未知属性、枚举反序列化大小写不敏感、Long 序列化为字符串 */
    public static final ObjectMapper mapper = new ObjectMapper();

    /** 使用蛇形（下划线）命名策略的 ObjectMapper，用于解析 snake_case 风格的 JSON */
    public static final ObjectMapper snakeMapper = new ObjectMapper();

    /** 启用默认类型信息（{@code @class} 属性）的 ObjectMapper，用于多态反序列化 */
    public static final ObjectMapper withTypeMapper = new ObjectMapper();

    /** 带类型 JSON 的字符串前缀 */
    private static final String CLASS_PREFIX = "{\"@class\":";

    /** 带类型 JSON 的 UTF-8 字节前缀，用于以 byte[] 形式判断 JSON 是否带类型 */
    private static final byte[] CLASS_PREFIX_BYTES = CLASS_PREFIX.getBytes(StandardCharsets.UTF_8);

    static {
        initMapperConfigure(mapper);
        initMapperConfigure(snakeMapper);
        initMapperConfigure(withTypeMapper);
        snakeMapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

        withTypeMapper.activateDefaultTyping(withTypeMapper.getPolymorphicTypeValidator(), ObjectMapper.DefaultTyping.NON_FINAL, JsonTypeInfo.As.PROPERTY);
    }

    /**
     * 为指定的 ObjectMapper 应用公共配置。
     *
     * @param mapper 待配置的 ObjectMapper
     */
    private static void initMapperConfigure(ObjectMapper mapper) {
        // 忽略未知属性
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS, true);

        // 序列化时将Long转为字符串
        SimpleModule simpleModule = new SimpleModule();
        // long -> String
        simpleModule.addSerializer(Long.TYPE, ToStringSerializer.instance);
        // Long -> String
        simpleModule.addSerializer(Long.class, ToStringSerializer.instance);
        mapper.registerModule(simpleModule);
        mapper.setTypeFactory(mapper.getTypeFactory().withClassLoader(Thread.currentThread().getContextClassLoader()));
    }

    /**
     * 将 JSON 字符串解析为指定类型的对象（别名方法，等价于 {@link #parseAsJson(Object, Class)}）。
     *
     * @param json  待解析的 JSON 字符串
     * @param clazz 目标类型
     * @param <T>   目标类型
     * @return 解析结果
     * @throws IOException 解析失败时抛出
     */
    public static <T> T parseJson(String json, Class<T> clazz) throws IOException {
        return parseAsJson(json, clazz);
    }

    /**
     * 将字符串或字节形式的 JSON 解析为指定类型的对象。
     *
     * <p>会自动根据 JSON 是否带类型信息（以 {@code {"@class":} 开头）选择对应的 ObjectMapper。</p>
     *
     * @param jsonObj 待解析的 JSON 字符串或 {@code byte[]}
     * @param clazz   目标类型
     * @param <T>     目标类型
     * @return 解析结果
     * @throws IOException 解析失败时抛出
     */
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

    /**
     * 将 snake_case 风格的 JSON 字符串解析为指定类型的对象。
     *
     * @param json  待解析的 JSON 字符串
     * @param clazz 目标类型
     * @param <T>   目标类型
     * @return 解析结果
     * @throws JsonProcessingException 解析失败时抛出
     */
    public static <T> T parseSnakeJson(String json, Class<T> clazz) throws JsonProcessingException {
        return snakeMapper.readValue(json, clazz);
    }

    /**
     * 将 snake_case 风格的 JSON 数组解析为 List。
     *
     * @param json        待解析的 JSON 数组字符串
     * @param elementType 列表元素类型
     * @param <T>         列表元素类型
     * @return 解析结果列表
     * @throws JsonProcessingException 解析失败时抛出
     */
    public static <T> List<T> parseSnakeJsonToList(String json, Class<T> elementType) throws JsonProcessingException {
        return snakeMapper.readValue(json, snakeMapper.getTypeFactory().constructCollectionType(List.class, elementType));
    }

    /**
     * 将 snake_case 风格的 JSON 对象解析为 Map。
     *
     * @param json 待解析的 JSON 对象字符串
     * @return 解析结果 Map
     * @throws JsonProcessingException 解析失败时抛出
     */
    public static Map<String, Object> parseSnakeJsonToMap(String json) throws JsonProcessingException {
        return snakeMapper.readValue(json, snakeMapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class));
    }

    /**
     * 将 JSON 数组解析为 List。
     *
     * @param json        待解析的 JSON 数组字符串
     * @param elementType 列表元素类型
     * @param <T>         列表元素类型
     * @return 解析结果列表
     * @throws JsonProcessingException 解析失败时抛出
     */
    public static <T> List<T> parseJsonToList(String json, Class<T> elementType) throws JsonProcessingException {
        return mapper.readValue(json, mapper.getTypeFactory().constructCollectionType(List.class, elementType));
    }

    /**
     * 将 JSON 对象解析为 Map（值为 Object）。
     *
     * @param json 待解析的 JSON 对象字符串
     * @return 解析结果 Map
     * @throws JsonProcessingException 解析失败时抛出
     */
    public static Map<String, Object> parseJsonToMap(String json) throws JsonProcessingException {
        return mapper.readValue(json, mapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class));
    }

    /**
     * 将 JSON 对象解析为指定键值类型的 Map。
     *
     * @param json       待解析的 JSON 对象字符串
     * @param keyClass   Map 的键类型
     * @param valueClass Map 的值类型
     * @param <K>        Map 的键类型
     * @param <V>        Map 的值类型
     * @return 解析结果 Map
     * @throws JsonProcessingException 解析失败时抛出
     */
    public static <K,V> Map<K, V> parseJsonToMap(String json, Class<K> keyClass, Class<V> valueClass) throws JsonProcessingException {
        return mapper.readValue(json, mapper.getTypeFactory().constructMapType(Map.class, keyClass, valueClass));
    }

    /**
     * 将对象序列化为 JSON 字符串。
     *
     * @param val 待序列化对象
     * @return JSON 字符串
     * @throws JsonProcessingException 序列化失败时抛出
     */
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

    /**
     * 将对象序列化为带类型信息（{@code @class} 属性）的 JSON 字符串。
     *
     * @param val 待序列化对象
     * @return 带类型信息的 JSON 字符串
     * @throws JsonProcessingException 序列化失败时抛出
     */
    public static String toJsonWithType(Object val) throws JsonProcessingException {
        return withTypeMapper.writeValueAsString(val);
    }

    /**
     * 更新所有 ObjectMapper 的 TypeFactory 类加载器，使其能访问指定 ClassLoader 中的类。
     * 用于在插件系统初始化后，让 Jackson 能解析插件中的类。
     *
     * @param classLoader 目标类加载器
     */
    public static void setTypeFactoryClassLoader(ClassLoader classLoader) {
        mapper.setTypeFactory(mapper.getTypeFactory().withClassLoader(classLoader));
        snakeMapper.setTypeFactory(snakeMapper.getTypeFactory().withClassLoader(classLoader));
        withTypeMapper.setTypeFactory(withTypeMapper.getTypeFactory().withClassLoader(classLoader));
    }
}
