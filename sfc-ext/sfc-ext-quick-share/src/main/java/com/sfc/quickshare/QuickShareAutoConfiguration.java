package com.sfc.quickshare;

import com.sfc.quickshare.model.QuickShareProperty;
import com.xiaotao.saltedfishcloud.service.config.ConfigService;
import com.xiaotao.saltedfishcloud.service.hello.HelloService;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@ComponentScan("com.sfc.quickshare")
@EntityScan("com.sfc.quickshare.model")
@EnableJpaRepositories(basePackages = "com.sfc.quickshare.repo")
public class QuickShareAutoConfiguration {

    @Bean
    public QuickShareProperty quickShareProperty(
            ConfigService configService,
            HelloService helloService
    ) {
        QuickShareProperty property = new QuickShareProperty();
        configService.bindPropertyEntity(property);
        helloService.setFeature("quickshare", property);
        return property;
    }
}
