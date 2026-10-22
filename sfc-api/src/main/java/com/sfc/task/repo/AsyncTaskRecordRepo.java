package com.sfc.task.repo;

import com.sfc.task.model.AsyncTaskRecord;
import com.sfc.cloud.data.repo.SfcBaseRepo;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Date;
import java.util.List;

/**
 * 异步任务记录仓库。
 * <p>任务队列以数据库为唯一事实来源：所有执行者（core 节点与微服务 worker）都通过
 * {@link #findWaitingByTaskTypes(Collection, Pageable)} + {@link #claim(Long, String, Date)}
 * 的乐观锁组合按任务类型认领任务。</p>
 */
public interface AsyncTaskRecordRepo extends SfcBaseRepo<AsyncTaskRecord> {
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
     * 查询指定任务类型集合中最早创建的等待中任务。
     * <p>这是类型路由调度的核心查询：执行者只会看到自己声明支持的任务类型，从根本上避免任务被
     * 无能力的实例抓取后反复回退。</p>
     *
     * @param taskTypes 执行者支持的任务类型集合，不可为空
     * @param pageable  分页参数，调用方应使用 {@code PageRequest.of(0, 1)} 只取一条
     * @return 按创建时间升序排列的等待中任务，没有匹配任务时为空列表
     */
    @Query("SELECT r FROM AsyncTaskRecord r WHERE r.status = 0 AND r.taskType IN :taskTypes ORDER BY r.createAt ASC")
    List<AsyncTaskRecord> findWaitingByTaskTypes(@Param("taskTypes") Collection<String> taskTypes, Pageable pageable);

    /**
     * 认领一个等待中的任务（乐观锁）。
     * <p>仅当任务仍处于等待中状态时才能认领成功，并发认领时只有一个执行者能成功。</p>
     *
     * @param taskId     任务id
     * @param workerId   认领者标识
     * @param claimedAt  认领时间
     * @return 受影响行数，大于0表示认领成功
     */
    @Modifying
    @Transactional
    @Query("UPDATE AsyncTaskRecord SET status = 1, claimedWorker = :workerId, claimedAt = :claimedAt, lastReportAt = :claimedAt "
            + "WHERE id = :taskId AND status = 0")
    int claim(@Param("taskId") Long taskId, @Param("workerId") String workerId, @Param("claimedAt") Date claimedAt);

    /**
     * 放弃任务执行权：把执行中的任务重置回等待中状态，并累加重试次数。
     * <p>用于执行者主动下线（优雅停机）或执行者失联后由健康检查回收任务。</p>
     *
     * @param taskId   任务id
     * @param maxRetry 最大重试次数（不含本次），达到该次数时不再重置
     * @return 受影响行数，大于0表示重置成功
     */
    @Modifying
    @Transactional
    @Query("UPDATE AsyncTaskRecord SET status = 0, claimedWorker = NULL, claimedAt = NULL, lastReportAt = NULL, "
            + "retryCount = COALESCE(retryCount, 0) + 1 "
            + "WHERE id = :taskId AND status = 1 AND COALESCE(retryCount, 0) < :maxRetry")
    int abandonAndRequeue(@Param("taskId") Long taskId, @Param("maxRetry") int maxRetry);

    /**
     * 将执行中但已超过最大重试次数的任务标记为失败。
     *
     * @param taskId   任务id
     * @param maxRetry 最大重试次数
     * @return 受影响行数，大于0表示标记成功
     */
    @Modifying
    @Transactional
    @Query("UPDATE AsyncTaskRecord SET status = 3 WHERE id = :taskId AND status = 1 AND COALESCE(retryCount, 0) >= :maxRetry")
    int failIfRetryExceeded(@Param("taskId") Long taskId, @Param("maxRetry") int maxRetry);

    /**
     * 查询所有运行中的任务
     */
    @Query("SELECT r FROM AsyncTaskRecord r WHERE r.status = 1")
    List<AsyncTaskRecord> listRunningTask();

    /**
     * 查询所有等待中任务的任务类型（去重）。
     * <p>用于健康检查发现「没有任何存活执行者支持」的任务类型。</p>
     */
    @Query("SELECT DISTINCT r.taskType FROM AsyncTaskRecord r WHERE r.status = 0")
    List<String> listWaitingTaskTypes();

}
