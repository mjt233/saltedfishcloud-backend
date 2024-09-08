package com.sfc.task.model;

import com.xiaotao.saltedfishcloud.model.template.AuditModel;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Index;
import javax.persistence.Table;

@Entity
@Getter
@Setter
@Table(indexes = {
        @Index(name = "idx_task_id", columnList = "taskId")
})
public class AsyncTaskLogRecord extends AuditModel {

    private Long taskId;

    @Column(columnDefinition = "LONGTEXT COMMENT '日志详情'")
    private String logInfo;
}
