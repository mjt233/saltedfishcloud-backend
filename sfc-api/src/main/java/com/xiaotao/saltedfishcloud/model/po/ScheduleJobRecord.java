package com.xiaotao.saltedfishcloud.model.po;

import com.sfc.cloud.data.model.SfcAuditModel;
import lombok.Getter;
import lombok.Setter;

import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * 定时任务记录
 */
@Entity
@Getter
@Setter
@Table(indexes = {
        @Index(name = "idx_job_name", columnList = "jobName")
})
public class ScheduleJobRecord extends SfcAuditModel {
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
