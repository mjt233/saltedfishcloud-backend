package com.xiaotao.saltedfishcloud.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 下拉选择项
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
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
