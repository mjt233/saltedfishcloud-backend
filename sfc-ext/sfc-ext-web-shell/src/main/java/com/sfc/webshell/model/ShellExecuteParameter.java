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
     * 使用的交互式shell解释器
     */
    private String shell;

    /**
     * 环境变量
     */
    private Map<String, String> env;

    /**
     * 编码
     */
    private String charset;

    /**
     * 限制执行超时(秒)，若小于等于0表示无限制
     */
    private long timeout;

    /**
     * 工作目录
     */
    private String workDirectory;

    /**
     * 会话初始名称
     */
    private String name;
}
