package com.saltedfishcloud.ext.ve.model;

import lombok.Data;

import java.util.Map;

/**
 * 媒体文件流信息
 */
@Data
public class MediaStream {
    /**
     * 流编号
     */
    private String no;

    /**
     * 备注
     */
    private String remark;

    /**
     * 流类型
     */
    private String type;

    /**
     * 元数据
     */
    private Map<String,String> metadata;

    /**
     * 码率（byte per second）
     */
    private Long bps;

    /**
     * 持续时长
     */
    private Long duration;

    /**
     * 原始行文本
     */
    private String originLine;
}
