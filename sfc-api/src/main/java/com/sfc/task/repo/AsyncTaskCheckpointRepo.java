package com.sfc.task.repo;

import com.sfc.task.model.AsyncTaskCheckpoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Date;
import java.util.List;

/**
 * 异步任务检查点仓库。
 *
 * <p>检查点与任务记录通过任务id一一对应，主键即任务id，因此直接使用 {@link JpaRepository}；
 * 任务的增删改状态流转仍在 {@link AsyncTaskRecordRepo}，两者互不影响。</p>
 */
public interface AsyncTaskCheckpointRepo extends JpaRepository<AsyncTaskCheckpoint, Long> {

    /**
     * 按任务id查询检查点。
     *
     * @param taskId 任务id
     * @return 检查点，不存在时返回 null
     */
    AsyncTaskCheckpoint findByTaskId(Long taskId);

    /**
     * 删除指定任务的检查点。
     *
     * @param taskId 任务id
     * @return 受影响行数，大于 0 表示删除成功
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM AsyncTaskCheckpoint c WHERE c.taskId = :taskId")
    int deleteByTaskId(@Param("taskId") Long taskId);

    /**
     * 批量删除最后更新时间早于指定阈值的检查点。
     *
     * @param threshold 更新时间阈值，早于该时间的检查点会被删除
     * @return 受影响行数
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM AsyncTaskCheckpoint c WHERE c.updateAt < :threshold")
    int deleteByUpdateAtBefore(@Param("threshold") Date threshold);

    /**
     * 批量查询给定任务id集合中已存在检查点的任务id。
     *
     * <p>用于故障转移回收任务时一次性判定哪些任务可以按「挂起」语义重新排队，
     * 避免逐条查询数据库。</p>
     *
     * @param taskIds 待查询的任务id集合，不可为空
     * @return 已存在检查点的任务id列表，没有时返回空列表
     */
    @Query("SELECT c.taskId FROM AsyncTaskCheckpoint c WHERE c.taskId IN :taskIds")
    List<Long> findExistingTaskIds(@Param("taskIds") Collection<Long> taskIds);
}
