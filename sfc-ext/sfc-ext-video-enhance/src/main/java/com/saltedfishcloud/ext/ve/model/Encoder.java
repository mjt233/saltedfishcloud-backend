package com.saltedfishcloud.ext.ve.model;

import lombok.Data;

/**
 * 编码器信息
 */
@Data
public class Encoder {
    /**
     * 类型
     */
    private String type;

    /**
     * 名称
     */
    private String name;

    /**
     * 描述
     */
    private String describe;

    /**
     * flag
     */
    private String flag;
}
