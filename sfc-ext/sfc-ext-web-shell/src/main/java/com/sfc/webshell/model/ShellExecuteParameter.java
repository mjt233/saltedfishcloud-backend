package com.sfc.webshell.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ShellExecuteParameter {
    /**
     * 完整命令
     */
    private String cmd;

    /**
     * 环境变量
     */
    private Map<String, String> env;

    /**
     * 编码
     */
    private String charset;
}
