package com.saltedfishcloud.ext.ve.model.po;

import com.xiaotao.saltedfishcloud.model.template.BaseModel;
import lombok.*;

import javax.persistence.Entity;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EncodeConvertTaskLog extends BaseModel {
    /**
     * 任务id
     */
    private Long taskId;

    /**
     * 任务日志消息
     */
    private String taskLog;
}
