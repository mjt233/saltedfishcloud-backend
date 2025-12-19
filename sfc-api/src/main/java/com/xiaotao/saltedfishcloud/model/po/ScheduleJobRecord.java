package com.xiaotao.saltedfishcloud.model.po;

import com.xiaotao.saltedfishcloud.model.template.AuditModel;
import lombok.Getter;
import lombok.Setter;

import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.util.Date;

/**
 * 定时任务记录
 */
@Entity
@Getter
@Setter
@Table(indexes = {
        @Index(name = "idx_job_name", columnList = "jobName")
})
public class ScheduleJobRecord extends AuditModel {
    /**
     * 任务名称
     */
    private String jobName;

    /**
     * 任务描述
     */
    private String jobDescribe;

    /**
     * 上次执行日期
     */
    private Long lastExecuteDate;
}
