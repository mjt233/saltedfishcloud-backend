package com.xiaotao.saltedfishcloud.config;

import org.springframework.context.annotation.Bean;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class RestTemplateConfig {

    @Bean
    public RestTemplate getRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setReadTimeout(120000);
        factory.setConnectTimeout(1200000);
        return new RestTemplate(factory);
    }
}
