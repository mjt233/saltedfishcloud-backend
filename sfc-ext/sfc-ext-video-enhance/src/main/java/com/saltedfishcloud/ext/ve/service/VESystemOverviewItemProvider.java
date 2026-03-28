package com.saltedfishcloud.ext.ve.service;

import com.saltedfishcloud.ext.ve.core.FFMpegHelper;
import com.saltedfishcloud.ext.ve.model.FFMpegInfo;
import com.saltedfishcloud.ext.ve.model.VEProperty;
import com.xiaotao.saltedfishcloud.common.SystemOverviewItemProvider;
import com.xiaotao.saltedfishcloud.model.ConfigNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
@Component
public class VESystemOverviewItemProvider implements SystemOverviewItemProvider {
    private final FFMpegHelper ffMpegHelper;

    @Override
    public List<ConfigNode> provideItem(Map<String, ConfigNode> existItem) {
        try {
            VEProperty property = ffMpegHelper.getProperty();
            FFMpegInfo ffMpegInfo = ffMpegHelper.getFFMpegInfo();
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
            log.error("Video Enhance插件 ffmpeg 加载配置出错: ", e);
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
