package com.saltedfishcloud.ext.ve.model;

import lombok.Data;

import java.util.Map;

/**
 * 视频封装信息
 */
@Data
public class FormatInfo {
    /**
     * 封装格式名称
     */
    private String formatName;

    /**
     * 封装格式详细名称
     */
    private String formatLongName;

    /**
     * 持续时长
     */
    private Double duration;

    /**
     * 码率
     */
    private Long bitRate;

    /**
     * 大小
     */
    private Long size;

    /**
     * 流数量
     */
    private Long nbStreams;

    /**
     * 脚本数量
     */
    private Long nbPrograms;

    private Map<String, String> tags;
}
