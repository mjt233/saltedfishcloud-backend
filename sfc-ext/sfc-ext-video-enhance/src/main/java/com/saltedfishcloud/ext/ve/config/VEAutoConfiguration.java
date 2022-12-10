package com.saltedfishcloud.ext.ve.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.saltedfishcloud.ext.ve.constant.VEConstants;
import com.saltedfishcloud.ext.ve.core.FFMpegHelper;
import com.saltedfishcloud.ext.ve.model.VEProperty;
import com.xiaotao.saltedfishcloud.service.config.ConfigService;
import com.xiaotao.saltedfishcloud.utils.MapperHolder;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan("com.saltedfishcloud.ext.ve")
public class VEAutoConfiguration {
    @Autowired
    private ConfigService configService;

    @Bean
    public FFMpegHelper ffMpegHelper() throws JsonProcessingException {
        VEProperty property = configService.getJsonConfig(VEConstants.PROPERTY_KEY, VEProperty.class);
        if (property == null) {
            property = new VEProperty();
        }
        FFMpegHelper ffMpegHelper = new FFMpegHelper(property);

        // 配置更新时，操作器也跟着更新
        configService.addBeforeSetListener(VEConstants.PROPERTY_KEY, json -> {
            try {
                VEProperty newProperty = MapperHolder.parseJson(json, VEProperty.class);
                BeanUtils.copyProperties(newProperty, ffMpegHelper.getProperty());

            } catch (JsonProcessingException e) {
                e.printStackTrace();
            }
        });
        return ffMpegHelper;
    }
}
