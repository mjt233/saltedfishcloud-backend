package com.sfc.webshell.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShellExecuteResult {

    /**
     * 退出代码
     */
    private int exitCode;

    /**
     * 耗时（ms）
     */
    private long time;

    /**
     * 标准输出内容
     */
    private String output;
}
