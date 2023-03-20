package com.sfc.quickshare;

import com.sfc.quickshare.model.QuickShareProperty;
import com.xiaotao.saltedfishcloud.service.config.ConfigService;
import com.xiaotao.saltedfishcloud.service.hello.HelloService;
import com.xiaotao.saltedfishcloud.utils.TypeUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@ComponentScan("com.sfc.quickshare")
@EntityScan("com.sfc.quickshare.model")
@EnableJpaRepositories(basePackages = "com.sfc.quickshare.repo")
public class QuickShareAutoConfiguration implements InitializingBean {
    @Autowired
    private ConfigService configService;

    @Autowired
    private HelloService helloService;

    @Bean
    public QuickShareProperty quickShareProperty() {
        return new QuickShareProperty();
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        // 绑定配置信息类与系统配置属性，保持同步
        QuickShareProperty property = quickShareProperty();
        configService.bindPropertyEntity(property);
        helloService.setFeature("quickshare", property);
    }
}
