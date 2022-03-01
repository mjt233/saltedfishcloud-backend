package com.xiaotao.saltedfishcloud.orm.config;

import com.xiaotao.saltedfishcloud.orm.config.aop.ConfigEntityProxy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;

@Slf4j
public class OrmConfigAutoConfigure {

    public OrmConfigAutoConfigure() {
        log.info("[ORM Config]配置自动映射服务开启");
    }

    @Bean
    public ConfigEntityProxy configEntityProxy() {
        return new ConfigEntityProxy();
    }

    @Bean
    public ConfigureManager configureManager() {
        return new ConfigureManager();
    }

}
