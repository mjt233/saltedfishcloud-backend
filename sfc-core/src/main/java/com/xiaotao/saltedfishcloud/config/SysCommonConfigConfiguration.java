package com.xiaotao.saltedfishcloud.config;

import com.xiaotao.saltedfishcloud.model.config.SysCommonConfig;
import com.xiaotao.saltedfishcloud.service.config.ConfigService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SysCommonConfigConfiguration {
    @Bean
    public SysCommonConfig sysCommonConfig(ConfigService configService) {
        SysCommonConfig config = new SysCommonConfig();
        configService.bindPropertyEntity(config);
        return config;
    }
}
