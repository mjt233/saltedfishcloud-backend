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
 * <p>任务认领通过「查询等待任务 + 原子更新状态」完成，保证同一任务不会被多个执行者同时认领。</p>
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
     * 查询指定任务类型集合中优先级最高的等待中任务（同优先级按创建时间升序）。
     * <p>查询结果包含紧急任务，执行者有容量时优先拿走紧急任务。</p>
     *
     * @param taskTypes 执行者支持的任务类型集合，不可为空
     * @param pageable  分页参数，调用方应使用 {@code PageRequest.of(0, 1)} 只取一条
     * @return 按优先级降序、创建时间升序排列的等待中任务，没有匹配任务时为空列表
     */
    @Query("SELECT r FROM AsyncTaskRecord r WHERE r.status = 0 AND r.taskType IN :taskTypes "
            + "ORDER BY COALESCE(r.priority, 0) DESC, r.createAt ASC")
    List<AsyncTaskRecord> findWaitingByTaskTypes(@Param("taskTypes") Collection<String> taskTypes, Pageable pageable);

    /**
     * 查询指定任务类型集合中的紧急等待任务（优先级不低于给定阈值），按创建时间升序排列。
     * <p>执行者满载时使用该查询，只认领紧急任务。</p>
     *
     * @param taskTypes       执行者支持的任务类型集合，不可为空
     * @param urgentPriority  紧急优先级阈值，优先级不低于该值的任务被视为紧急任务
     * @param pageable        分页参数，调用方应使用 {@code PageRequest.of(0, 1)} 只取一条
     * @return 紧急等待任务列表，没有匹配任务时为空列表
     */
    @Query("SELECT r FROM AsyncTaskRecord r WHERE r.status = 0 AND r.taskType IN :taskTypes "
            + "AND COALESCE(r.priority, 0) >= :urgentPriority ORDER BY r.createAt ASC")
    List<AsyncTaskRecord> findUrgentWaitingByTaskTypes(@Param("taskTypes") Collection<String> taskTypes,
                                                       @Param("urgentPriority") Integer urgentPriority,
                                                       Pageable pageable);

    /**
     * 认领一个等待中的任务（乐观锁）。
     * <p>仅当任务仍处于等待中状态时才能认领成功，并发认领时只有一个执行者能成功。</p>
     *
     * @param taskId     任务id
     * @param serviceId  认领者所属服务名（服务发现中的 serviceId）
     * @param workerId   认领者实例地址（{@code host:port}）
     * @param role       认领者角色，取值见 {@link com.sfc.task.AsyncTaskConstants.WorkerRole}
     * @param claimedAt  认领时间
     * @return 受影响行数，大于0表示认领成功
     */
    @Modifying
    @Transactional
    @Query("UPDATE AsyncTaskRecord SET status = 1, claimedService = :serviceId, claimedWorker = :workerId, "
            + "claimedRole = :role, claimedAt = :claimedAt, lastReportAt = :claimedAt "
            + "WHERE id = :taskId AND status = 0")
    int claim(@Param("taskId") Long taskId,
              @Param("serviceId") String serviceId,
              @Param("workerId") String workerId,
              @Param("role") String role,
              @Param("claimedAt") Date claimedAt);

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
    @Query("UPDATE AsyncTaskRecord SET status = 0, claimedService = NULL, claimedWorker = NULL, claimedRole = NULL, "
            + "claimedAt = NULL, lastReportAt = NULL, retryCount = COALESCE(retryCount, 0) + 1 "
            + "WHERE id = :taskId AND status = 1 AND COALESCE(retryCount, 0) < :maxRetry")
    int abandonAndRequeue(@Param("taskId") Long taskId, @Param("maxRetry") int maxRetry);

    /**
     * 把执行中但已超过最大重试次数的任务标记为失败。
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
     * 直接把执行中的任务标记为失败（不重试）。
     * <p>用于临时任务在故障转移时的收尾：临时任务不参与重试，执行者失联即置为失败。</p>
     *
     * @param taskId 任务id
     * @return 受影响行数，大于0表示标记成功
     */
    @Modifying
    @Transactional
    @Query("UPDATE AsyncTaskRecord SET status = 3 WHERE id = :taskId AND status = 1")
    int failRunningTask(@Param("taskId") Long taskId);

    /**
     * 查询所有运行中的任务
     */
    @Query("SELECT r FROM AsyncTaskRecord r WHERE r.status = 1")
    List<AsyncTaskRecord> listRunningTask();

    /**
     * 统计创建时间早于指定阈值的等待中任务，按任务类型分组。
     * <p>用于健康检查发现「长时间无人认领」的任务并输出告警，便于运维发现任务类型写错或
     * 对应执行者未部署的情况。</p>
     *
     * @param threshold 创建时间阈值，早于该时间的等待任务会被统计
     * @return 每行依次为 [任务类型, 任务数量, 最早创建时间]
     */
    @Query("SELECT r.taskType, COUNT(r), MIN(r.createAt) FROM AsyncTaskRecord r "
            + "WHERE r.status = 0 AND r.createAt < :threshold GROUP BY r.taskType")
    List<Object[]> statWaitingBefore(@Param("threshold") Date threshold);

}
