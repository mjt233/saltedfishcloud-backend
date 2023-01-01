package com.saltedfishcloud.ext.ve.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import static com.saltedfishcloud.ext.ve.constant.VEConstants.EncoderType.*;

/**
 * 媒体流编码转换规则
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Accessors(chain = true)
public class EncodeConvertRule {
    /**
     * 流索引
     */
    private String index;

    /**
     * 处理方法,copy或convert
     */
    private String method;

    /**
     * 使用的编码器
     */
    private String encoder;

    /**
     * 流类型，video，audio或subtitle
     */
    private String type;

    /**
     * 比特率
     */
    private String bitRate;

    /**
     * 根据流类型，获取对应的编码器类型标志
     */
    public String getTypeFlag() {
        if (AUDIO.equals(type)) {
            return "a";
        } else if (VIDEO.equals(type)) {
            return "v";
        } else if (SUBTITLE.equals(type)) {
            return "s";
        } else {
            throw new IllegalArgumentException("未知类型：" + type);
        }
    }
}
