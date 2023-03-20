package com.xiaotao.saltedfishcloud.model;

import com.xiaotao.saltedfishcloud.annotations.ConfigProperty;
import com.xiaotao.saltedfishcloud.annotations.ConfigPropertyEntity;
import lombok.Data;

/**
 * 背景图选项实体
 */
@Data
@ConfigPropertyEntity
public class BgOption {

    @ConfigProperty(value = "enabled", title = "状态")
    private Boolean enabled;

    @ConfigProperty(value = "url", title = "图片url")
    private String url;

    @ConfigProperty(value = "operacity", title = "不透明度")
    private Double operacity;

    @ConfigProperty(value = "size", title = "尺寸")
    private String size;
}
