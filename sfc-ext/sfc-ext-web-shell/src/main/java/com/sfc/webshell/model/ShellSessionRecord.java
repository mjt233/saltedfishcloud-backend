package com.sfc.webshell.model;

import com.xiaotao.saltedfishcloud.model.template.AuditModel;
import lombok.*;

/**
 * Shell会话记录
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShellSessionRecord extends AuditModel {
    /**
     * 执行主机
     */
    private String host;

    /**
     * 是否运行中
     */
    private Boolean running;

    /**
     * 会话名称
     */
    private String name;

    /**
     * 进程退出代码
     */
    private Integer exitCode;

    /**
     * 会话初始化参数
     */
    private ShellExecuteParameter parameter;
}
