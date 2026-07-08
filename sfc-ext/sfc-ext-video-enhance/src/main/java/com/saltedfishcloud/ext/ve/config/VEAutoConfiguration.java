package com.saltedfishcloud.ext.ve.config;

import com.saltedfishcloud.ext.ve.constant.VEConstants;
import com.saltedfishcloud.ext.ve.core.FFMpegHelper;
import com.saltedfishcloud.ext.ve.core.FFMpegHelperImpl;
import com.saltedfishcloud.ext.ve.model.VEProperty;
import com.xiaotao.saltedfishcloud.service.config.ConfigService;
import com.xiaotao.saltedfishcloud.utils.FileUtils;
import com.xiaotao.saltedfishcloud.utils.MapperHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.io.IOException;
import java.util.Optional;

@Configuration
@ComponentScan("com.saltedfishcloud.ext.ve")
@EntityScan("com.saltedfishcloud.ext.ve.model")
@EnableJpaRepositories(basePackages = "com.saltedfishcloud.ext.ve.dao")
@Slf4j
public class VEAutoConfiguration implements InitializingBean {

    @Bean
    public VEProperty veProperty(ConfigService configService) throws IOException {
        VEProperty property = Optional.ofNullable(configService.getJsonConfig(VEConstants.PROPERTY_KEY, VEProperty.class))
                .orElseGet(VEProperty::new);

        // 配置更新时，操作器也跟着更新
        configService.addBeforeSetListener(VEConstants.PROPERTY_KEY, json -> {
            try {
                VEProperty newProperty = MapperHolder.parseJson(json, VEProperty.class);
                BeanUtils.copyProperties(newProperty, property);
            } catch (IOException e) {
                log.error("视频增强插件配置更新出错", e);
            }
        });
        return property;
    }

    @Bean
    public FFMpegHelper ffMpegHelper(VEProperty veProperty) throws IOException {
        return new FFMpegHelperImpl(veProperty);
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        FileUtils.setContentTypeMapping("wasm", "application/wasm");
    }
}
