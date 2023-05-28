package com.saltedfishcloud.ext.ve.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.saltedfishcloud.ext.ve.constant.VEConstants;
import com.saltedfishcloud.ext.ve.core.FFMpegHelper;
import com.saltedfishcloud.ext.ve.core.FFMpegHelperImpl;
import com.saltedfishcloud.ext.ve.model.FFMpegInfo;
import com.saltedfishcloud.ext.ve.model.VEProperty;
import com.saltedfishcloud.ext.ve.service.SubtitleResourceHandler;
import com.saltedfishcloud.ext.ve.service.VideoInfoResourceHandler;
import com.xiaotao.saltedfishcloud.common.SystemOverviewItemProvider;
import com.xiaotao.saltedfishcloud.model.ConfigNode;
import com.xiaotao.saltedfishcloud.service.config.ConfigService;
import com.xiaotao.saltedfishcloud.service.resource.ResourceService;
import com.xiaotao.saltedfishcloud.utils.MapperHolder;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Configuration
@ComponentScan("com.saltedfishcloud.ext.ve")
@EntityScan("com.saltedfishcloud.ext.ve.model")
@EnableJpaRepositories(basePackages = "com.saltedfishcloud.ext.ve.dao")
public class VEAutoConfiguration implements SystemOverviewItemProvider {
    @Autowired
    private ConfigService configService;

    @Autowired
    private ResourceService resourceService;

    @Bean
    public SubtitleResourceHandler subtitleResourceHandler() {
        SubtitleResourceHandler handler = new SubtitleResourceHandler();
        resourceService.addResourceHandler(handler);
        return handler;
    }

    @Bean
    public VideoInfoResourceHandler videoInfoResourceHandler() {
        VideoInfoResourceHandler handler = new VideoInfoResourceHandler();
        resourceService.addResourceHandler(handler);
        return handler;
    }

    @Bean
    public FFMpegHelper ffMpegHelper() throws IOException {
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
                e.printStackTrace();
            }
        });
        return ffMpegHelper;
    }

    @Override
    public List<ConfigNode> provideItem(Map<String, ConfigNode> existItem) {
        try {
            VEProperty property = ffMpegHelper().getProperty();
            FFMpegInfo ffMpegInfo = ffMpegHelper().getFFMpegInfo();
            return Arrays.asList(
                    ConfigNode.builder()
                            .title("ffmpeg配置")
                            .name("ffmpeg-configure")
                            .nodes(Arrays.asList(
                                    new ConfigNode("ffmpeg路径", property.getFfmpegPath()),
                                    new ConfigNode("版本", ffMpegInfo.getVersion()),
                                    new ConfigNode("构建信息", ffMpegInfo.getBuilt()),
                                    new ConfigNode("编译参数", ffMpegInfo.getConfiguration())
                                            .useTemplate("configure-info")
                            ))
                            .build(),
                    ConfigNode.builder()
                            .title("ffmpeg编码器信息")
                            .name("ffmpeg-encoders")
                            .nodes(Arrays.asList(
                                    new ConfigNode("视频编码器", ffMpegInfo.getVideoEncoders())
                                            .useTemplate("encoder-info"),
                                    new ConfigNode("音频编码器", ffMpegInfo.getAudioEncoders())
                                            .useTemplate("encoder-info"),
                                    new ConfigNode("字幕编码器", ffMpegInfo.getSubtitleEncoders())
                                            .useTemplate("encoder-info"),
                                    new ConfigNode("其他编码器", ffMpegInfo.getOtherEncoders())
                                            .useTemplate("encoder-info")
                            ))
                            .build()
                    );
        } catch (Exception e) {
            e.printStackTrace();

            return List.of(
                    ConfigNode.builder()
                            .title("ffmpeg配置")
                            .name("ffmpeg-config")
                            .nodes(Collections.singletonList(new ConfigNode("错误", e.getMessage())))
                            .build()
            );
        }
    }
}
