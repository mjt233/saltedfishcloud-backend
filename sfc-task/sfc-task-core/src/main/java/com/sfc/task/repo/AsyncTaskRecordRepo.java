package com.sfc.task.repo;

import com.sfc.task.model.AsyncTaskRecord;
import org.apache.ibatis.annotations.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface AsyncTaskRecordRepo extends JpaRepository<AsyncTaskRecord, Long> {

    /**
     * 修改任务状态。
     * 需要传入原状态是作为并发控制的乐观锁版本号机制
     * @param taskId        异步任务id
     * @param newStatus     要修改的状态
     * @param originStatus  原状态
     * @return              受影响的行数，大于0时则为修改成功。
     */
    @Modifying
    @Transactional
    @Query("UPDATE async_task_record SET status = :newStatus WHERE id = :taskId AND status = :originStatus")
    int updateStatus(@Param("taskId") Long taskId, @Param("newStatus") Integer newStatus, @Param("originStatus") Integer originStatus);
}
