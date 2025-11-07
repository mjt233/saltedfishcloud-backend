package com.sfc.ext.apkparser;

import org.springframework.context.annotation.Bean;

public class ApkParserAutoConfiguration {

    @Bean
    public ApkIconThumbnailHandler apkIconThumbnailHandler() {
        return new ApkIconThumbnailHandler();
    }
}
