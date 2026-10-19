package com.sfc.task.model;

import com.sfc.cloud.data.model.SfcAuditModel;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(indexes = {
        @Index(name = "idx_async_task_log_record_task_id", columnList = "taskId")
})
public class AsyncTaskLogRecord extends SfcAuditModel {

    private Long taskId;

    @Lob
    private String logInfo;
}
