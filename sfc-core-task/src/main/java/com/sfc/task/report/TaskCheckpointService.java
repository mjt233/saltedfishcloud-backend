package com.sfc.task.report;

import com.sfc.task.model.AsyncTaskCheckpoint;
import com.sfc.task.repo.AsyncTaskCheckpointRepo;
import com.xiaotao.saltedfishcloud.utils.CollectionUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 任务检查点服务。
 *
 * <p>检查点是执行者上报的任务自定义现场快照（JSON 文本），用于任务被挂起或执行者失联后
 * 由其他执行者接管时恢复现场。本服务负责检查点的 upsert、查询、终态清理与过期清理，
 * 不解析检查点内容。</p>
 *
 * <p>检查点与任务记录通过任务id一一对应：任务进入终态（完成/失败/取消）后由
 * {@code TaskReportService} 删除；任务被挂起、执行者失联时保留，供接管者使用。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskCheckpointService {

    /**
     * 日志前缀
     */
    private static final String LOG_PREFIX = "[任务检查点]";

    /**
     * 单个检查点允许的最大长度（字符数），1MB；超过后拒绝保存，避免异常任务写爆数据库
     */
    public static final int MAX_CHECKPOINT_LENGTH = 1024 * 1024;

    /**
     * 残留检查点的最长保留时间：超过该时长未被更新的检查点视为孤儿数据
     */
    private static final Duration MAX_CHECKPOINT_AGE = Duration.ofDays(30);

    /**
     * 过期检查点清理任务的执行周期（毫秒）：每日一次
     */
    private static final long CLEANUP_INTERVAL_MILLIS = 24 * 60 * 60 * 1000L;

    /**
     * 过期检查点清理任务的首次执行延迟（毫秒）：启动 10 分钟后开始，避免与启动流程争抢数据库
     */
    private static final long CLEANUP_INITIAL_DELAY_MILLIS = 10 * 60 * 1000L;

    /**
     * 批量查询检查点时单批的最大任务数量
     */
    private static final int QUERY_BATCH_SIZE = 500;

    /**
     * 检查点仓库
     */
    private final AsyncTaskCheckpointRepo checkpointRepo;

    /**
     * 保存（新建或覆盖）一个任务检查点。
     *
     * <p>检查点为空白时视为删除该任务的检查点；超过 {@value #MAX_CHECKPOINT_LENGTH} 个字符时
     * 记录 WARN 并拒绝本次保存（保留原有检查点）。</p>
     *
     * @param taskId     任务id，为 null 时忽略
     * @param taskType   任务类型，仅用于排查
     * @param checkpoint 检查点内容（JSON 文本），语义由任务实现自定义
     */
    @Transactional
    public void save(Long taskId, String taskType, String checkpoint) {
        if (taskId == null) {
            log.warn("{}忽略缺少任务id的检查点保存请求", LOG_PREFIX);
            return;
        }
        if (checkpoint == null || checkpoint.isBlank()) {
            delete(taskId);
            return;
        }
        if (checkpoint.length() > MAX_CHECKPOINT_LENGTH) {
            log.warn("{}任务[{}]的检查点长度 {} 超过上限 {}，已拒绝保存", LOG_PREFIX, taskId,
                    checkpoint.length(), MAX_CHECKPOINT_LENGTH);
            return;
        }
        AsyncTaskCheckpoint entity = checkpointRepo.findByTaskId(taskId);
        boolean created = entity == null;
        if (created) {
            entity = new AsyncTaskCheckpoint();
            entity.setTaskId(taskId);
        }
        entity.setTaskType(taskType);
        entity.setCheckpoint(checkpoint);
        entity.setUpdateAt(new Date());
        checkpointRepo.save(entity);
        log.debug("{}{}任务[{}]的检查点，长度 {}", LOG_PREFIX, created ? "新建" : "更新", taskId, checkpoint.length());
    }

    /**
     * 查询任务的检查点。
     *
     * @param taskId 任务id
     * @return 检查点内容，任务没有检查点时返回 null
     */
    public String find(Long taskId) {
        if (taskId == null) {
            return null;
        }
        AsyncTaskCheckpoint entity = checkpointRepo.findByTaskId(taskId);
        return entity == null ? null : entity.getCheckpoint();
    }

    /**
     * 删除任务的检查点（任务进入终态时调用）。
     *
     * @param taskId 任务id，为 null 时忽略
     */
    @Transactional
    public void delete(Long taskId) {
        if (taskId == null) {
            return;
        }
        if (checkpointRepo.deleteByTaskId(taskId) > 0) {
            log.debug("{}已删除任务[{}]的检查点", LOG_PREFIX, taskId);
        }
    }

    /**
     * 清理最后更新时间早于给定阈值的残留检查点。
     *
     * <p>正常情况下任务进入终态时检查点已被删除，本方法只兜底清理进程崩溃等异常场景遗留的孤儿数据。</p>
     *
     * @param maxAge 允许的最大未更新时长；为 null、负数或 0 时不做清理
     * @return 已删除的检查点数量
     */
    @Transactional
    public int cleanupExpired(Duration maxAge) {
        if (maxAge == null || maxAge.isZero() || maxAge.isNegative()) {
            return 0;
        }
        Date threshold = new Date(System.currentTimeMillis() - maxAge.toMillis());
        int deleted = checkpointRepo.deleteByUpdateAtBefore(threshold);
        if (deleted > 0) {
            log.info("{}已清理 {} 个超过 {} 未更新的残留检查点", LOG_PREFIX, deleted, maxAge);
        }
        return deleted;
    }

    /**
     * 批量查询给定任务id集合中已存在检查点的任务id。
     *
     * <p>用于故障转移回收任务时一次性判定哪些任务可按「挂起」语义重新排队（保留检查点、
     * 不消耗重试次数），避免逐条查询数据库。</p>
     *
     * @param taskIds 待查询的任务id集合，为空时返回空集合
     * @return 已存在检查点的任务id集合
     */
    public Set<Long> findExistingTaskIds(Collection<Long> taskIds) {
        if (CollectionUtils.isEmpty(taskIds)) {
            return Set.of();
        }
        Set<Long> result = new LinkedHashSet<>();
        for (List<Long> batch : CollectionUtils.partition(taskIds, QUERY_BATCH_SIZE)) {
            List<Long> existing = checkpointRepo.findExistingTaskIds(batch);
            if (existing != null) {
                result.addAll(existing);
            }
        }
        return result;
    }

    /**
     * 每日清理一次超过保留期（30 天）未更新的残留检查点；异常只记录日志。
     */
    @Scheduled(fixedDelay = CLEANUP_INTERVAL_MILLIS, initialDelay = CLEANUP_INITIAL_DELAY_MILLIS)
    public void cleanupExpiredCheckpoints() {
        try {
            cleanupExpired(MAX_CHECKPOINT_AGE);
        } catch (Throwable e) {
            log.warn("{}清理过期检查点失败: {}", LOG_PREFIX, e.getMessage());
        }
    }
}
