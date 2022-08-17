package com.xiaotao.saltedfishcloud.model;

import lombok.Data;

import java.util.List;

@Data
public class ConfigNodeGroup {
    /**
     * 配置组名称
     */
    private String name;

    /**
     * 配置域节点
     */
    private String area;

    /**
     * 配置节点列表
     */
    private List<ConfigNode> nodes;

    /**
     * 是否在菜单中隐藏
     */
    private boolean hide;
}
