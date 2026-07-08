package com.xiaotao.saltedfishcloud.config;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.std.ToStringSerializer;

/**
 * Jackson 3 序列化器配置。
 */
@Configuration
public class JacksonConfig {

    /**
     * 注册 JsonMapperBuilderCustomizer，将 Long/long 序列化为 String 避免前端 JS 精度丢失，并禁用空 Bean 序列化失败。
     *
     * @return JsonMapperBuilderCustomizer 实例
     */
    @Bean
    public JsonMapperBuilderCustomizer jsonMapperBuilderCustomizer() {
        SimpleModule longToStringModule = new SimpleModule();
        // long -> String
        longToStringModule.addSerializer(Long.TYPE, ToStringSerializer.instance);
        // Long -> String
        longToStringModule.addSerializer(Long.class, ToStringSerializer.instance);
        return builder -> builder
                .addModule(longToStringModule)
                .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
    }
}
