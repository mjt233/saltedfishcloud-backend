package com.saltedfishcloud.ext.ve.model;

import lombok.Data;

import java.util.Map;

@Data
public class StreamInfo {
    /**
     * 流索引/编号
     */
    private String index;

    /**
     * 编码
     */
    private String codecName;

    /**
     * 编码详细名称信息
     */
    private String codecLongName;

    /**
     * 流编码类型
     */
    private String codecType;

    /**
     * 持续时长秒数
     */
    private Double duration;

    /**
     * 视频宽度
     */
    private Long width;

    /**
     * 视频高度
     */
    private Long height;

    /**
     * 平均帧率
     */
    private Double avgFrameRate;

    public void setAvgFrameRate(String avgFrameRate) {
        if (avgFrameRate == null || "0/0".equals(avgFrameRate)) {
            return;
        }
        String[] split = avgFrameRate.split("/");
        if (split.length != 2) {
            return;
        }

        this.avgFrameRate = Double.parseDouble(split[0]) / Double.parseDouble(split[1]);
    }

    /**
     * 音频采样格式
     */
    private String sampleFmt;

    /**
     * 音频采样率
     */
    private String sampleRate;

    /**
     * 声道数
     */
    private Long channels;

    /**
     * 声道布局
     */
    private String channelLayout;

    /**
     * 码率
     */
    private Long bitRate;

    /**
     * 字幕语言
     */
    private String language;

    /**
     * 字幕标题
     */
    private String title;

    private Map<String, String> disposition;

    /**
     * 其他标签
     */
    private Map<String, String> tags;

    public void setTags(Map<String, String> tags) {
        this.tags = tags;
        if (this.tags == null) {
            return;
        }
        this.title = tags.get("title");
        this.language = tags.get("language");
    }
}
