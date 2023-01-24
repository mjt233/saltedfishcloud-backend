package com.xiaotao.saltedfishcloud.model;

import com.xiaotao.saltedfishcloud.annotations.ConfigProperties;
import com.xiaotao.saltedfishcloud.annotations.ConfigPropertiesEntity;
import lombok.Data;

/**
 * 背景图选项实体
 */
@Data
@ConfigPropertiesEntity
public class BgOption {

    @ConfigProperties(value = "enabled", title = "状态")
    private Boolean enabled;

    @ConfigProperties(value = "url", title = "图片url")
    private String url;

    @ConfigProperties(value = "operacity", title = "不透明度")
    private Double operacity;

    @ConfigProperties(value = "size", title = "尺寸")
    private String size;
}
