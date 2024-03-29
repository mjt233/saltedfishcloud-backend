package com.sfc.task.model;

import com.xiaotao.saltedfishcloud.model.template.AuditModel;
import lombok.*;

import javax.persistence.Entity;

@Entity
@Getter
@Setter
public class AsyncTaskLogRecord extends AuditModel {

    private Long taskId;

    private String logInfo;
}
