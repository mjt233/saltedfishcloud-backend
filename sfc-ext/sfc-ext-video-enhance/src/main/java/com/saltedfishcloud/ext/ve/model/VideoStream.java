package com.saltedfishcloud.ext.ve.model;

import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class VideoStream extends MediaStream {
    /**
     * 编码
     */
    private String encode;

    /**
     * 分辨率
     */
    private String resolution;

    /**
     * 帧率
     */
    private Double frameRage;
}
