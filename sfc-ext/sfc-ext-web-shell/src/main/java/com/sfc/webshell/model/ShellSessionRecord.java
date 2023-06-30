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


}