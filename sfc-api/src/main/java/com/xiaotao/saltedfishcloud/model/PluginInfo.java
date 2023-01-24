package com.xiaotao.saltedfishcloud.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.xiaotao.saltedfishcloud.enums.PluginLoadType;
import com.xiaotao.saltedfishcloud.enums.PluginType;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 拓展插件信息
 */
@Data
@Accessors(chain = true)
public class PluginInfo {
    public static final int PLUGIN_UNLOADED = 0;
    public static final int PLUGIN_LOADED = 1;
    public static final int PLUGIN_WAIT_DELETE = 2;

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

    /**
     * 需要自动加载的静态资源
     */
    private List<String> autoLoad;

    /**
     * 状态，0 - 待加载，1 - 已加载，2 - 待删除
     */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Integer status;

    /**
     * 是否通过jar包在插件目录中加载的
     */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Boolean isJar;

    /**
     * 插件url
     */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String url;

    /**
     * 待升级版本
     */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String upgradeVersion;
}
