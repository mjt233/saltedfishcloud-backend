package com.saltedfishcloud.ext.ve.model;

import lombok.Data;
import lombok.EqualsAndHashCode;


@EqualsAndHashCode(callSuper = true)
@Data
public class AudioStream extends MediaStream {
    /**
     * 编码
     */
    private String encode;

    /**
     * 采样率
     */
    private Long sampleRate;

    /**
     * 模式（单声道/双声道）
     */
    private String mode;
}
