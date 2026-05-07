package com.sfc.task.repo;

import com.sfc.task.model.AsyncTaskRecord;
import com.xiaotao.saltedfishcloud.dao.BaseRepo;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Date;
import java.util.List;

public interface AsyncTaskRecordRepo extends BaseRepo<AsyncTaskRecord> {
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
    @Query("UPDATE AsyncTaskRecord SET status = :newStatus WHERE id = :taskId AND status = :originStatus")
    int updateStatus(@Param("taskId") Long taskId, @Param("newStatus") Integer newStatus, @Param("originStatus") Integer originStatus);

    /**
     * 将运行中的任务状态标记为已离线
     * @param taskIds   要标记的任务id集合
     */
    @Modifying
    @Transactional
    @Query("UPDATE AsyncTaskRecord SET status = 5 WHERE id IN :taskIds AND status = 1")
    void setTaskOffline(@Param("taskIds")Collection<Long> taskIds);

    @Query("SELECT r FROM AsyncTaskRecord r WHERE r.status = 1")
    List<AsyncTaskRecord> listRunningTask();

    @Query("SELECT r FROM AsyncTaskRecord r WHERE r.status = :status AND r.createAt <= :createAt")
    List<AsyncTaskRecord> findByStatusAndCreateAt(@Param("status") Integer status,@Param("createAt") Date createAt);

}
