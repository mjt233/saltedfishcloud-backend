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
        // todo 实现配置服务接口与bean对象的快捷绑定同步功能
        QuickShareProperty property = quickShareProperty();
        configService.addAfterSetListener(QuickShareProperty.KEY_IS_ENABLE, v -> {
            property.setIsEnabled(TypeUtils.toBoolean(v));
        });

        configService.addAfterSetListener(QuickShareProperty.KEY_EFFECTIVE_DURATION, v -> {
            property.setEffectiveDuration(TypeUtils.toLong(v));
        });

        configService.addAfterSetListener(QuickShareProperty.KEY_MAX_SIZE, v -> {
            property.setMaxSize(TypeUtils.toLong(v));
        });

        String isEnable = configService.getConfig(QuickShareProperty.KEY_IS_ENABLE);
        if (isEnable != null) {
            property.setIsEnabled(TypeUtils.toBoolean(isEnable));
        }

        String maxSize = configService.getConfig(QuickShareProperty.KEY_MAX_SIZE);
        if (maxSize != null) {
            property.setMaxSize(TypeUtils.toLong(maxSize));
        }

        String effectiveDuration = configService.getConfig(QuickShareProperty.KEY_EFFECTIVE_DURATION);
        if (effectiveDuration != null) {
            property.setEffectiveDuration(TypeUtils.toLong(effectiveDuration));
        }


        helloService.bindConfigAsFeature(QuickShareProperty.KEY_EFFECTIVE_DURATION, QuickShareProperty.KEY_EFFECTIVE_DURATION, Long.class);
        helloService.bindConfigAsFeature(QuickShareProperty.KEY_MAX_SIZE, QuickShareProperty.KEY_MAX_SIZE, Long.class);
        helloService.bindConfigAsFeature(QuickShareProperty.KEY_IS_ENABLE, QuickShareProperty.KEY_IS_ENABLE, Boolean.class);
        helloService.setFeature(QuickShareProperty.KEY_EFFECTIVE_DURATION, property.getEffectiveDuration());
        helloService.setFeature(QuickShareProperty.KEY_MAX_SIZE, property.getMaxSize());
        helloService.setFeature(QuickShareProperty.KEY_IS_ENABLE, property.getIsEnabled());
    }
}
