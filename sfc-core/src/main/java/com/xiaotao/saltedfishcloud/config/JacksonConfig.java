package com.xiaotao.saltedfishcloud.config;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.SerializationFeature;

/**
 * Jackson 3 序列化器配置。
 */
@Configuration
public class JacksonConfig {

    /**
     * 注册 JsonMapperBuilderCustomizer，禁用空 Bean 序列化失败。
     *
     * @return JsonMapperBuilderCustomizer 实例
     */
    @Bean
    public JsonMapperBuilderCustomizer jsonMapperBuilderCustomizer() {
        return builder -> builder.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
    }
}
