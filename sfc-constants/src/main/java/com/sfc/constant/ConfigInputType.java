package com.sfc.constant;

/**
 * 表单配置项的输入类型
 */
public interface ConfigInputType {
    /**
     * 普通文本输入
     */
    String TEXT = "text";

    /**
     * 开关
     */
    String SWITCH = "switch";

    /**
     * 下拉选择，需要搭配 ConfigNode#options 使用
     */
    String SELECT = "select";

    /**
     * 表示使用指定的组件模板类型，需要搭配 ConfigNode#template 和 ConfigNode#params(可选) 属性使用
     */
    String TEMPLATE = "template";
}
