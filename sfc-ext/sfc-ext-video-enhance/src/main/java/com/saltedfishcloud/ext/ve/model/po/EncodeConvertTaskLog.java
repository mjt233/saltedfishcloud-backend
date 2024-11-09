package com.saltedfishcloud.ext.ve.model.po;

import com.xiaotao.saltedfishcloud.model.template.BaseModel;
import lombok.*;

import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(indexes = {
        @Index(name = "idx_task_id", columnList = "taskId")
})
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
