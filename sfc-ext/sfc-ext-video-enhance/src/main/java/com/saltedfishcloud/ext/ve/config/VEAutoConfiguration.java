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
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.io.IOException;

@Configuration
@ComponentScan("com.saltedfishcloud.ext.ve")
@EntityScan("com.saltedfishcloud.ext.ve.model")
@EnableJpaRepositories(basePackages = "com.saltedfishcloud.ext.ve.dao")
@Slf4j
public class VEAutoConfiguration implements InitializingBean {

    @Bean
    public FFMpegHelper ffMpegHelper(ConfigService configService) throws IOException {
        VEProperty property = configService.getJsonConfig(VEConstants.PROPERTY_KEY, VEProperty.class);
        if (property == null) {
            property = new VEProperty();
        }
        FFMpegHelper ffMpegHelper = new FFMpegHelperImpl(property);

        // 配置更新时，操作器也跟着更新
        configService.addBeforeSetListener(VEConstants.PROPERTY_KEY, json -> {
            try {
                VEProperty newProperty = MapperHolder.parseJson(json, VEProperty.class);
                BeanUtils.copyProperties(newProperty, ffMpegHelper.getProperty());
            } catch (IOException e) {
                log.error("视频增强插件配置更新出错", e);
            }
        });
        return ffMpegHelper;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        FileUtils.setContentTypeMapping("wasm", "application/wasm");
    }
}
