package com.xiaotao.saltedfishcloud.ext.mp3thumbnail.config;

import com.xiaotao.saltedfishcloud.ext.mp3thumbnail.handler.Mp3CoverThumbnailHandler;
import com.xiaotao.saltedfishcloud.service.file.thumbnail.ThumbnailHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class Mp3ThumbnailAutoConfiguration {

    @Bean
    public ThumbnailHandler mp3ThumbnailHandler() {
        return new Mp3CoverThumbnailHandler();
    }
}
