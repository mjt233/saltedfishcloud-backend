package com.sfc.task.model;

import com.xiaotao.saltedfishcloud.model.template.AuditModel;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(indexes = {
        @Index(name = "idx_task_id", columnList = "taskId")
})
public class AsyncTaskLogRecord extends AuditModel {

    private Long taskId;

    @Lob
    private String logInfo;
}
