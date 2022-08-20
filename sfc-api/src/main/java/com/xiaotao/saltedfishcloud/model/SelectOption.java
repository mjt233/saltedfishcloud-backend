package com.xiaotao.saltedfishcloud.model;

import lombok.Data;

/**
 * 下拉选择项
 */
@Data
public class SelectOption {
    /**
     * 显示标题
     */
    private String title;

    /**
     * 内容值
     */
    private String value;

    /**
     * 选择动作
     */
    private String action;
}
