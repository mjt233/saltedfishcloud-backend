package com.xiaotao.saltedfishcloud.model;

import lombok.Data;

import java.util.List;

@Data
public class ConfigNode {
    /**
     * 配置节点
     */
    private String name;

    /**
     * 配置标题
     */
    private String title;

    /**
     * 配置值
     */
    private String value;

    /**
     * 默认值
     */
    private String defaultValue;

    /**
     * 描述
     */
    private String describe;

    /**
     * 是否只读
     */
    private boolean readonly;

    /**
     * 是否禁用
     */
    private boolean disabled;

    /**
     * 输入类型
     */
    private String inputType;

    /**
     * 可选项，用于为select、multi-select、ratio和checkbox类型提供可选值
     */
    private List<NameValueType> options;

    /**
     * 子节点，用于为form表单类型提供子表单数据，子表单的值为json格式化字符串
     */
    private List<ConfigNodeGroup> subNode;

    /**
     * 图标
     */
    private String icon;

    /**
     * 当类型为template时使用模板内容作为内容编辑
     */
    private String template;

    /**
     * 是否在菜单中隐藏
     */
    private boolean hide;
}
