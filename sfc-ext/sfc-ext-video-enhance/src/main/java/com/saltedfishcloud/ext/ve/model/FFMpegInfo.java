package com.saltedfishcloud.ext.ve.model;

import lombok.Data;

import java.util.List;

@Data
public class FFMpegInfo {
    /**
     * 视频编码器列表
     */
    private List<Encoder> videoEncoders;

    /**
     * 音频编码器列表
     */
    private List<Encoder> audioEncoders;

    /**
     * 标题编码器列表
     */
    private List<Encoder> subtitleEncoders;

    /**
     * 其他编码器列表
     */
    private List<Encoder> otherEncoders;

    /**
     * 版本
     */
    private String version;

    /**
     * 构建信息
     */
    private String built;

    /**
     * 编译参数信息
     */
    private String configuration;

}
