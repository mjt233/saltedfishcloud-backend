package com.sfc.task.report;

import com.sfc.cloud.task.worker.protocol.TaskAbandonParam;
import com.sfc.cloud.task.worker.protocol.TaskFinishReportParam;
import com.sfc.cloud.task.worker.protocol.TaskLogReportParam;
import com.sfc.cloud.task.worker.protocol.TaskProgressReportParam;
import com.sfc.cloud.task.worker.protocol.TaskReportStartParam;
import com.sfc.task.AsyncTaskConstants;
import com.sfc.task.constants.TaskMQTopic;
import com.sfc.task.model.AsyncTaskLogRecord;
import com.sfc.task.model.AsyncTaskRecord;
import com.sfc.task.prog.DynamicProgressRecord;
import com.sfc.task.prog.ProgressRecord;
import com.sfc.task.repo.AsyncTaskLogRecordRepo;
import com.sfc.task.repo.AsyncTaskRecordRepo;
import com.sfc.task.schedule.TaskScheduleConstants;
import com.sfc.common.utils.SfcStringUtils;
import com.xiaotao.saltedfishcloud.cache.CacheKeyPrefixes;
import com.xiaotao.saltedfishcloud.cache.CacheService;
import com.xiaotao.saltedfishcloud.service.mq.MQService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 任务执行上报服务。
 * <p>负责接收执行者（core 内嵌执行者与微服务实例）的任务执行上报（开始、进度、日志、完成、放弃），
 * 并维护任务状态、运行日志与进度快照。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskReportService {
    private static final String LOG_PREFIX = "[异步任务上报]";

    /**
     * 进度快照的缓存有效期（秒）。
     */
    private static final long PROGRESS_TTL_SECONDS = 10;

    private final AsyncTaskRecordRepo taskRecordRepo;

    private final AsyncTaskLogRecordRepo logRecordRepo;

    private final TaskLogBufferService logBufferService;

    private final MQService mqService;

    private final CacheService cacheService;

    /**
     * 处理任务开始执行上报。
     *
     * @param param 上报参数
     * @return 处理成功返回 true；任务不存在、已结束或不属于该执行者时返回 false
     */
    public boolean reportStart(TaskReportStartParam param) {
        AsyncTaskRecord record = getOwnedRunningRecord(param.taskId(), param.workerId());
        if (record == null) {
            return false;
        }
        Date now = new Date();
        if (record.getExecuteDate() == null) {
            record.setExecuteDate(now);
        }
        record.setExecutor(param.workerId());
        record.setLastReportAt(now);
        taskRecordRepo.save(record);
        return true;
    }

    /**
     * 处理任务进度上报，写入统一的任务进度快照缓存。
     *
     * <p>固定字段之外的扩展字段同样写入快照，查询进度时随进度一起返回。</p>
     *
     * @param param 上报参数
     */
    public void reportProgress(TaskProgressReportParam param) {
        if (param.taskId() == null) {
            return;
        }
        DynamicProgressRecord progress = new DynamicProgressRecord(param.extensions());
        progress.setLoaded(Optional.ofNullable(param.loaded()).orElse(0L))
                .setTotal(Optional.ofNullable(param.total()).orElse(-1L))
                .setSpeed(Optional.ofNullable(param.speed()).orElse(0L))
                .setLastUpdateTime(Optional.ofNullable(param.lastUpdateTime()).orElse(System.currentTimeMillis()));
        cacheService.set(CacheKeyPrefixes.TASK_PROGRESS + param.taskId(), progress,
                PROGRESS_TTL_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * 读取任务的最新进度快照。
     *
     * @param taskId 任务id
     * @return 进度快照，缓存中不存在或已过期时返回 null
     */
    public ProgressRecord getProgress(Long taskId) {
        if (taskId == null) {
            return null;
        }
        Object value = cacheService.get(CacheKeyPrefixes.TASK_PROGRESS + taskId);
        return value instanceof ProgressRecord record ? record : null;
    }

    /**
     * 处理任务运行日志上报：写入运行日志缓冲，并推送到日志消息主题供前端实时监听。
     *
     * @param param 上报参数
     */
    public void reportLog(TaskLogReportParam param) {
        if (param.taskId() == null || param.lines() == null || param.lines().isEmpty()) {
            return;
        }
        logBufferService.append(param.taskId(), param.lines());
        for (String line : param.lines()) {
            try {
                mqService.push(TaskMQTopic.Get.ASYNC_TASK_LOG(param.taskId()), line);
            } catch (Exception e) {
                log.debug("{}推送任务[{}]日志到消息主题失败: {}", LOG_PREFIX, param.taskId(), e.getMessage());
            }
        }
    }

    /**
     * 处理任务执行结束上报。
     *
     * @param param 上报参数
     * @return 处理成功返回 true；任务不存在、已结束或不属于该执行者时返回 false
     */
    public boolean reportFinish(TaskFinishReportParam param) {
        int status = Optional.ofNullable(param.status()).orElse(AsyncTaskConstants.Status.FAILED);
        if (!AsyncTaskConstants.Status.FINISH.equals(status)
                && !AsyncTaskConstants.Status.FAILED.equals(status)
                && !AsyncTaskConstants.Status.CANCEL.equals(status)) {
            log.warn("{}非法的任务结束状态: {}-{}", LOG_PREFIX, param.taskId(), status);
            return false;
        }
        AsyncTaskRecord record = getOwnedRunningRecord(param.taskId(), param.workerId());
        if (record == null) {
            return false;
        }
        if (param.tailLogLines() != null && !param.tailLogLines().isEmpty()) {
            logBufferService.append(param.taskId(), param.tailLogLines());
        }
        List<String> lines = logBufferService.drain(param.taskId());
        saveLogRecord(record, lines, SfcStringUtils.hasText(param.message())
                ? "[" + new Date() + "] " + param.message()
                : null);

        Date now = new Date();
        record.setStatus(status);
        record.setLastReportAt(now);
        if (AsyncTaskConstants.Status.FINISH.equals(status)) {
            record.setFinishDate(now);
            record.setFailedDate(null);
        } else {
            record.setFailedDate(now);
            record.setIsFailed(AsyncTaskConstants.Status.FAILED.equals(status));
        }
        taskRecordRepo.save(record);
        notifyTaskExit(record);
        destroyLogTopicLater(param.taskId());
        log.info("{}任务 {}-{} 执行结束，状态: {}", LOG_PREFIX, record.getName(), record.getId(), status);
        return true;
    }

    /**
     * 处理任务执行权放弃上报：保存已产生的日志，把任务重置回等待中状态并重新发布；
     * 若已达到最大重试次数则置为失败。临时任务不参与故障转移重试，放弃后直接置为失败。
     *
     * @param param 上报参数
     * @return 任务已重置为等待中返回 true
     */
    public boolean reportAbandon(TaskAbandonParam param) {
        AsyncTaskRecord record = getOwnedRunningRecord(param.taskId(), param.workerId());
        if (record == null) {
            return false;
        }
        String message = Optional.ofNullable(param.message()).orElse("无");
        List<String> lines = logBufferService.drain(param.taskId());
        if (Boolean.TRUE.equals(record.getIsTemp())) {
            saveLogRecord(record, lines, "[" + new Date() + "] 执行者放弃执行（临时任务不重试，置为失败）: " + message);
            failTempTask(record, "临时任务被放弃执行，已置为失败（临时任务不参与故障转移重试）");
            return false;
        }
        saveLogRecord(record, lines, "[" + new Date() + "] 执行者放弃执行: " + message);

        if (taskRecordRepo.abandonAndRequeue(param.taskId(), TaskScheduleConstants.MAX_RETRY) > 0) {
            log.warn("{}任务 {}-{} 被放弃执行，已重置为等待重试", LOG_PREFIX, record.getName(), record.getId());
            mqService.sendBroadcast(TaskMQTopic.TASK_PUBLISH, param.taskId());
            return true;
        }
        markFailedIfRetryExceeded(record);
        return false;
    }

    /**
     * 取消一个执行者已失联的运行中任务。
     *
     * @param taskId 任务id
     * @param reason 取消失败原因说明
     * @return 成功取消返回 true
     */
    public boolean cancelRunningTask(Long taskId, String reason) {
        if (taskId == null) {
            return false;
        }
        AsyncTaskRecord record = taskRecordRepo.findById(taskId).orElse(null);
        if (record == null || !AsyncTaskConstants.Status.RUNNING.equals(record.getStatus())) {
            return false;
        }
        if (taskRecordRepo.updateStatus(taskId, AsyncTaskConstants.Status.CANCEL,
                AsyncTaskConstants.Status.RUNNING) <= 0) {
            return false;
        }
        saveLogRecord(record, logBufferService.drain(taskId),
                "[" + new Date() + "] 任务被取消: " + Optional.ofNullable(reason).orElse("执行者已失联"));
        record.setStatus(AsyncTaskConstants.Status.CANCEL);
        record.setFailedDate(new Date());
        notifyTaskExit(record);
        log.warn("{}任务 {}-{} 被取消: {}", LOG_PREFIX, record.getName(), taskId, reason);
        return true;
    }

    /**
     * 回收执行者已失联的任务：保存日志后把任务重置为等待中并重新发布。
     * <p>若任务已达到最大重试次数，则直接置为失败。</p>
     *
     * @param records 需要回收的任务记录
     * @param reason  回收原因说明
     */
    public void reclaimDeadWorkerTasks(Collection<AsyncTaskRecord> records, String reason) {
        if (records == null || records.isEmpty()) {
            return;
        }
        for (AsyncTaskRecord record : records) {
            try {
                List<String> lines = logBufferService.drain(record.getId());
                saveLogRecord(record, lines, "[" + new Date() + "] " + reason);
                // 临时任务不参与故障转移重试，执行者失联时直接置为失败
                if (Boolean.TRUE.equals(record.getIsTemp())) {
                    failTempTask(record, "临时任务执行者失联，已置为失败（临时任务不参与故障转移重试）");
                    continue;
                }
                if (taskRecordRepo.abandonAndRequeue(record.getId(), TaskScheduleConstants.MAX_RETRY) > 0) {
                    log.warn("{}任务 {}-{} 执行者失联，已重置为等待重试: {}",
                            LOG_PREFIX, record.getName(), record.getId(), reason);
                    mqService.sendBroadcast(TaskMQTopic.TASK_PUBLISH, record.getId());
                } else {
                    markFailedIfRetryExceeded(record);
                }
            } catch (Exception e) {
                log.error("{}回收任务 {}-{} 失败", LOG_PREFIX, record.getName(), record.getId(), e);
            }
        }
    }

    /**
     * 把达到最大重试次数的任务置为失败并广播退出事件。
     */
    private void markFailedIfRetryExceeded(AsyncTaskRecord record) {
        if (taskRecordRepo.failIfRetryExceeded(record.getId(), TaskScheduleConstants.MAX_RETRY) <= 0) {
            return;
        }
        AsyncTaskRecord latest = taskRecordRepo.findById(record.getId()).orElse(record);
        latest.setStatus(AsyncTaskConstants.Status.FAILED);
        latest.setFailedDate(new Date());
        latest.setIsFailed(true);
        // 失败时间等字段需要显式落库（failIfRetryExceeded 只更新了 status）
        taskRecordRepo.save(latest);
        log.error("{}任务 {}-{} 达到最大重试次数，已置为失败", LOG_PREFIX, latest.getName(), latest.getId());
        notifyTaskExit(latest);
        destroyLogTopicLater(latest.getId());
    }

    /**
     * 把执行中的临时任务直接置为失败（临时任务不参与故障转移重试）。
     *
     * @param record 任务记录
     * @param reason 失败原因说明
     */
    private void failTempTask(AsyncTaskRecord record, String reason) {
        if (taskRecordRepo.failRunningTask(record.getId()) <= 0) {
            return;
        }
        AsyncTaskRecord latest = taskRecordRepo.findById(record.getId()).orElse(record);
        latest.setStatus(AsyncTaskConstants.Status.FAILED);
        latest.setFailedDate(new Date());
        latest.setIsFailed(true);
        // 失败时间等字段需要显式落库（failRunningTask 只更新了 status）
        taskRecordRepo.save(latest);
        log.error("{}任务 {}-{} {}", LOG_PREFIX, latest.getName(), latest.getId(), reason);
        notifyTaskExit(latest);
        destroyLogTopicLater(latest.getId());
    }

    /**
     * 获取任务记录，并校验其处于运行中且由指定执行者认领。
     *
     * @return 校验通过的任务记录；校验不通过时返回 null
     */
    private AsyncTaskRecord getOwnedRunningRecord(Long taskId, String workerId) {
        if (taskId == null || !SfcStringUtils.hasText(workerId)) {
            return null;
        }
        AsyncTaskRecord record = taskRecordRepo.findById(taskId).orElse(null);
        if (record == null) {
            log.warn("{}任务 {} 不存在，忽略上报", LOG_PREFIX, taskId);
            return null;
        }
        if (!AsyncTaskConstants.Status.RUNNING.equals(record.getStatus())
                || !Objects.equals(workerId, record.getClaimedWorker())) {
            log.warn("{}忽略过期上报：任务 {}-{} 状态 {}，认领者为 {}，上报者为 {}",
                    LOG_PREFIX, record.getName(), taskId, record.getStatus(), record.getClaimedWorker(), workerId);
            return null;
        }
        return record;
    }

    /**
     * 保存一条任务日志历史记录。
     *
     * @param record  任务记录
     * @param lines   日志行
     * @param extra   追加到末尾的说明信息，可为 null
     */
    private void saveLogRecord(AsyncTaskRecord record, List<String> lines, String extra) {
        List<String> allLines = new ArrayList<>(lines == null ? List.of() : lines);
        if (SfcStringUtils.hasText(extra)) {
            allLines.add(extra);
        }
        if (allLines.isEmpty()) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (String line : allLines) {
            sb.append(line).append('\n');
        }
        AsyncTaskLogRecord logRecord = new AsyncTaskLogRecord();
        logRecord.setTaskId(record.getId());
        logRecord.setUid(record.getUid());
        logRecord.setLogInfo(sb.toString());
        logRecordRepo.save(logRecord);
    }

    /**
     * 广播任务退出事件，唤醒正在阻塞等待任务结束的调用方。
     */
    private void notifyTaskExit(AsyncTaskRecord record) {
        try {
            mqService.sendBroadcast(TaskMQTopic.Get.ASYNC_TASK_EXIT(record.getId()), record);
        } catch (Exception e) {
            log.error("{}广播任务退出事件失败: {}-{}", LOG_PREFIX, record.getName(), record.getId(), e);
        }
    }

    /**
     * 延迟销毁任务日志消息主题。
     */
    private void destroyLogTopicLater(Long taskId) {
        CompletableFuture.delayedExecutor(5, TimeUnit.SECONDS)
                .execute(() -> {
                    try {
                        mqService.destroyQueue(TaskMQTopic.Get.ASYNC_TASK_LOG(taskId));
                    } catch (Exception e) {
                        log.debug("{}销毁任务日志消息主题失败: {}-{}", LOG_PREFIX, taskId, e.getMessage());
                    }
                });
    }
}
