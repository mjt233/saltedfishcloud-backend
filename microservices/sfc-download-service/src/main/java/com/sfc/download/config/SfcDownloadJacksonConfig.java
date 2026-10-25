package com.sfc.download.config;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.std.ToStringSerializer;

/**
 * 本服务的 Jackson 3 序列化配置。
 *
 * <p>与宿主 {@code sfc-core} 的 {@code JacksonConfig} 保持同一口径：</p>
 * <ul>
 *   <li>把 {@code long} / {@link Long} 统一序列化为字符串，避免前端 JavaScript 处理雪花 id、
 *       文件大小等 64 位数值时发生精度丢失（前端契约中 id、taskId、size、loaded、speed
 *       等字段均为字符串）；</li>
 *   <li>关闭 {@code FAIL_ON_NULL_FOR_PRIMITIVES}：允许 JSON 中的 {@code null} 写入原始类型字段；</li>
 *   <li>关闭 {@code FAIL_ON_EMPTY_BEANS}：避免代理对象或空 Bean 序列化时直接 500。</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
public class SfcDownloadJacksonConfig {

    /**
     * 注册 {@link JsonMapperBuilderCustomizer}，统一配置本服务的 JSON 序列化行为。
     *
     * @return JSON 构建器定制器
     */
    @Bean
    public JsonMapperBuilderCustomizer longToStringJsonMapperBuilderCustomizer() {
        SimpleModule longToStringModule = new SimpleModule();
        // long -> String
        longToStringModule.addSerializer(Long.TYPE, ToStringSerializer.instance);
        // Long -> String
        longToStringModule.addSerializer(Long.class, ToStringSerializer.instance);
        return builder -> builder
                .addModule(longToStringModule)
                .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
    }
}
