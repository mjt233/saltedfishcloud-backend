package com.xiaotao.saltedfishcloud.dao.jpa;

import com.xiaotao.saltedfishcloud.model.po.ScheduleJobRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

public interface ScheduleJobRecordRepo extends JpaRepository<ScheduleJobRecord, Long> {
    ScheduleJobRecord getByJobName(String jobName);

    /**
     * 基于乐观锁机制的更新上次执行时间
     * @param jobName       任务名称
     * @param date          设置的日期
     * @param originDate    上次更新日期
     * @return              大于0表示更新成功
     */
    @Modifying
    @Transactional
    @Query("UPDATE ScheduleJobRecord SET lastExecuteDate = :date WHERE jobName = :jobName AND lastExecuteDate = :originDate")
    int updateExecuteDate(@Param("jobName") String jobName, @Param("date")Long date, @Param("originDate") Long originDate);
}
