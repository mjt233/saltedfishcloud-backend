package com.saltedfishcloud.ext.ve.dao;

import com.saltedfishcloud.ext.ve.model.po.EncodeConvertTask;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EncodeConvertTaskRepo extends JpaRepository<EncodeConvertTask, Long> {

    /**
     * 根据用户id和任务状态获取视频编码转换任务列表。由于后续改用了系统统一的异步任务机制，任务状态改查异步任务表
     * @param uid       用户id
     * @param status    任务状态{@link com.sfc.task.AsyncTaskConstants.Status}
     * @param pageable  分页信息
     */
//    @Query( nativeQuery = true, value = "SELECT convert_task.*,record.status as task_status FROM encode_convert_task convert_task" +
//            " LEFT JOIN async_task_record record ON convert_task.task_id = record.id " +
//            " WHERE convert_task.uid = ?1 AND record.status = ?2" +
//            " ORDER BY record.create_at DESC")
    @Query("SELECT task FROM EncodeConvertTask task " +
            "WHERE task.uid = :uid AND task.asyncTaskRecord.status = :status " +
            "ORDER BY task.createAt DESC")
    Page<EncodeConvertTask> findByUidAndTaskStatus(@Param("uid") Long uid,@Param("status") Integer status, Pageable pageable);
}
