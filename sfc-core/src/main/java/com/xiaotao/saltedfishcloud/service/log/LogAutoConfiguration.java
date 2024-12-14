package com.xiaotao.saltedfishcloud.service.log;

import com.xiaotao.saltedfishcloud.model.config.SysLogConfig;
import com.xiaotao.saltedfishcloud.service.config.ConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class LogAutoConfiguration {
    private final ConfigService configService;

    @Bean
    public SysLogConfig sysLogConfig() {
        SysLogConfig sysLogConfig = new SysLogConfig();
        configService.bindPropertyEntity(sysLogConfig);
        return sysLogConfig;
    }


}
