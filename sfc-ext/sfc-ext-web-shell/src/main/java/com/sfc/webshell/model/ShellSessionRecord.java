package com.sfc.webshell.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
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
}
