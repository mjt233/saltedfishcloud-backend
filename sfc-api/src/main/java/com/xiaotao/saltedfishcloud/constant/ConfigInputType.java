package com.xiaotao.saltedfishcloud.constant;

/**
 * 表单配置项的输入类型。
 * 防止限得太死，暂时用字符串常量定义，后续计划彻底完善配置系统时再考虑枚举类型
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

    /**
     * 子表单类型，通常使用JSON对象保存一个复杂配置结构
     */
    String FORM = "form";
}
