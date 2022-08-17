package com.xiaotao.saltedfishcloud.model;

import lombok.Data;

import java.util.List;

/**
 * 插件能提供的配置信息
 */
@Data
public class PluginConfigNodeInfo {
    /**
     * 插件名称
     */
    private String name;

    /**
     * 插件图标
     */
    private String icon;

    /**
     * 该插件的配置组
     */
    private List<ConfigNodeGroup> groups;
}
