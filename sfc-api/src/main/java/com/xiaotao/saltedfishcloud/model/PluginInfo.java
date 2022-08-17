package com.xiaotao.saltedfishcloud.model;

import com.xiaotao.saltedfishcloud.enums.PluginLoadType;
import com.xiaotao.saltedfishcloud.enums.PluginType;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 拓展插件信息
 */
@Data
@Accessors(chain = true)
public class PluginInfo {
    /**
     * 插件名称
     */
    private String name;

    /**
     * 插件别名
     */
    private String alias;

    /**
     * 插件类加载方式
     */
    private PluginLoadType loadType;

    /**
     * 插件类型
     */
    private PluginType type;

    /**
     * 插件描述
     */
    private String describe;

    /**
     * 路径
     */
    private String path;

    /**
     * 插件作者
     */
    private String author;

    /**
     * 作者邮箱
     */
    private String email;

    /**
     * 插件版本
     */
    private String version;

    /**
     * 插件API版本
     */
    private String apiVersion;

    /**
     * 插件图标
     */
    private String icon;
}
