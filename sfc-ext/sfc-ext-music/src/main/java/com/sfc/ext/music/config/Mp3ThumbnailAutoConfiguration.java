package com.sfc.ext.music.config;

import com.sfc.ext.music.handler.Mp3CoverThumbnailHandler;
import com.xiaotao.saltedfishcloud.service.file.thumbnail.ThumbnailHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import({
        Mp3CoverThumbnailHandler.class
})
public class Mp3ThumbnailAutoConfiguration {
}
