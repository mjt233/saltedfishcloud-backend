package com.sfc.task.repo;

import com.sfc.task.model.AsyncTaskRecord;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Date;
import java.util.List;

public interface AsyncTaskRecordRepo extends JpaRepository<AsyncTaskRecord, Long>, JpaSpecificationExecutor<AsyncTaskRecord> {

    /**
     * 根据用户id和状态查询任务记录
     * @param uid       用户id
     * @param status    任务状态
     * @param pageable  分页信息
     * @return          查询结果
     */
    List<AsyncTaskRecord> findByUidAndStatusIn(Long uid, List<Integer> status, Pageable pageable);

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

    /**
     * 将运行中的任务状态标记为已离线
     * @param taskIds   要标记的任务id集合
     */
    @Modifying
    @Transactional
    @Query("UPDATE async_task_record SET status = 5 WHERE id IN :taskIds AND status = 1")
    void setTaskOffline(@Param("taskIds")Collection<Long> taskIds);

    @Query(value = "SELECT * FROM async_task_record WHERE status = 5",nativeQuery = true)
    List<AsyncTaskRecord> listOfflineTask();

    @Query(value = "SELECT * FROM async_task_record WHERE status = 1",nativeQuery = true)
    List<AsyncTaskRecord> listRunningTask();

    @Query("SELECT r FROM async_task_record r WHERE r.status = :status AND r.createAt <= :createAt")
    List<AsyncTaskRecord> findByStatusAndCreateAt(@Param("status") Integer status,@Param("createAt") Date createAt);

}
