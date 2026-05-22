package com.saltedfishcloud.ext.samba.config;

import com.saltedfishcloud.ext.samba.filesystem.SambaStorageFactory;
import com.xiaotao.saltedfishcloud.service.file.thumbnail.ThumbnailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class SambaFileSystemAutoConfiguration implements InitializingBean {

    @Override
    public void afterPropertiesSet() throws Exception {
        log.warn("[Samba存储]samba存储插件未开发完成，暂时不可用。");
    }

    @Bean
    public SambaStorageFactory sambaDiskFileSystemFactory(ThumbnailService thumbnailService) {
        return new SambaStorageFactory(thumbnailService);
    }
}
