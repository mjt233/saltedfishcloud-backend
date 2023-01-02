package com.xiaotao.saltedfishcloud.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 桌面小组件原型信息
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DesktopComponent {
    /**
     * 组件描述标题
     */
    private String title;

    /**
     * 描述介绍
     */
    private String describe;

    /**
     * 组件名称
     */
    private String name;

    /**
     * 配置原型参数列表，深度为1
     */
    private List<ConfigNode> config;

    /**
     * 图标
     */
    private String icon;

    /**
     * 显示顺序
     */
    private Integer showOrder;
}
