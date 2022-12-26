package com.saltedfishcloud.ext.ve.model.po;

import com.xiaotao.saltedfishcloud.model.template.AuditModel;
import lombok.*;

import javax.persistence.Entity;
import javax.persistence.Table;

@Entity
@Table(name = "encode_convert_task")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EncodeConvertTask extends AuditModel {

    /**
     * 系统异步任务id
     */
    private String taskId;

    /**
     * 任务状态
     */
    private Integer taskStatus;

    /**
     * 任务类型
     */
    private String type;

    /**
     * 任务JSON参数
     */
    private String params;
}
