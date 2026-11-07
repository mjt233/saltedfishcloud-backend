package com.sfc.task;

import com.sfc.cloud.data.id.SfcIdUtil;
import com.sfc.cloud.data.validation.SfcUIDValidator;
import com.sfc.common.constant.SfcCommonError;
import com.sfc.common.constant.SfcUserConstants;
import com.sfc.common.exception.SfcJsonException;
import com.sfc.common.utils.SfcStringUtils;
import com.sfc.task.constants.TaskMQTopic;
import com.sfc.task.model.AsyncTaskCreateParam;
import com.sfc.task.model.AsyncTaskLogRecord;
import com.sfc.task.model.AsyncTaskRecord;
import com.sfc.task.prog.ProgressRecord;
import com.sfc.task.report.TaskLogBufferService;
import com.sfc.task.report.TaskReportService;
import com.sfc.task.repo.AsyncTaskLogRecordRepo;
import com.sfc.task.repo.AsyncTaskRecordRepo;
import com.sfc.task.worker.TaskInterruptRouter;
import com.xiaotao.saltedfishcloud.service.mq.MQService;
import com.xiaotao.saltedfishcloud.utils.ResourceUtils;
import com.xiaotao.saltedfishcloud.utils.SecureUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static com.sfc.task.constants.TaskMQTopic.Get.ASYNC_TASK_EXIT;

/**
 * 默认的异步任务管理器实现。
 * <p>任务管理器是异步任务系统的唯一入口与统一管理层，负责：</p>
 * <ul>
 *     <li>任务提交：写入数据库队列并广播唤醒执行者；</li>
 *     <li>任务生命周期：状态流转、执行者认领信息与结果落库；</li>
 *     <li>统一查询：日志、进度、状态，不区分任务由 core 内嵌执行者还是微服务执行；</li>
 *     <li>执行控制：中断请求按任务的认领者路由到对应执行者。</li>
 * </ul>
 * <p>任务的认领与执行由 sfc-task-worker SDK 提供的执行者完成，本类不持有执行器。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultAsyncTaskManagerImpl implements AsyncTaskManager {
    private static final String LOG_PREFIX = "[异步任务管理]";

    private final AsyncTaskRecordRepo repo;

    private final AsyncTaskLogRecordRepo logRepo;

    private final MQService mqService;

    private final TaskInterruptRouter interruptRouter;

    private final TaskLogBufferService logBufferService;

    private final AsyncTaskFactoryRegistry factoryRegistry;

    private final TaskReportService reportService;

    /**
     * 将 Spring 容器中所有实现了 AsyncTaskFactory 的 bean 注册到任务工厂注册表。
     *
     * @param factories 容器中的任务工厂列表，可为 null
     */
    @Autowired(required = false)
    public void setFactories(List<AsyncTaskFactory> factories) {
        if (factories != null) {
            for (AsyncTaskFactory factory : factories) {
                registerFactory(factory);
            }
        }
    }

    @Override
    public void registerFactory(AsyncTaskFactory factory) {
        factoryRegistry.register(factory);
    }

    @Override
    public void submitAsyncTask(AsyncTaskRecord record) throws IOException {
        record.setStatus(AsyncTaskConstants.Status.WAITING);
        record.setPriority(Optional.ofNullable(record.getPriority()).orElse(AsyncTaskConstants.Priority.NORMAL));
        record.setClaimedService(null);
        record.setClaimedWorker(null);
        record.setClaimedRole(null);
        record.setClaimedAt(null);
        record.setLastReportAt(null);
        repo.save(record);
        mqService.sendBroadcast(TaskMQTopic.TASK_PUBLISH, record.getId());
        log.info("{}提交任务 {}-{}，类型: {}，优先级: {}", LOG_PREFIX, record.getName(), record.getId(),
                record.getTaskType(), record.getPriority());
    }

    @Override
    public AsyncTaskRecord createTask(AsyncTaskCreateParam param) throws IOException {
        Long uid = Objects.requireNonNull(SecureUtils.getSpringSecurityUser()).getId();
        return submitTaskForUser(uid, param);
    }

    @Override
    public AsyncTaskRecord submitTaskForUser(Long uid, AsyncTaskCreateParam param) throws IOException {
        if (!SfcStringUtils.hasText(param.getName())) {
            throw new IllegalArgumentException("任务名称 name 不能为空");
        }
        if (!SfcStringUtils.hasText(param.getTaskType())) {
            throw new IllegalArgumentException("任务类型 taskType 不能为空");
        }
        AsyncTaskRecord record = AsyncTaskRecord.builder()
                .name(param.getName())
                .taskType(param.getTaskType())
                .params(param.getParams())
                .cpuOverhead(param.getCpuOverhead())
                .isTemp(param.getIsTemp())
                .priority(Boolean.TRUE.equals(param.getImmediate())
                        ? AsyncTaskConstants.Priority.URGENT
                        : AsyncTaskConstants.Priority.NORMAL)
                .build();
        record.setUid(uid == null ? SfcUserConstants.PUBLIC_USER_ID : uid);

        submitAsyncTask(record);
        return record;
    }

    @Override
    public Resource getTaskLog(Long taskId, boolean withHistory) {
        // 任务可能会被多次重试执行，从而产生多比日志记录，这里需要拼接起来
        List<AsyncTaskLogRecord> logs = logRepo.findByTaskId(taskId);
        String allLog = Optional.ofNullable(logs)
                .orElseGet(Collections::emptyList)
                .stream()
                .map(e -> "----------------[历史日志][ " + e.getCreateAt() + "]----------------\n" + e.getLogInfo())
                .collect(Collectors.joining("\n"));
        AsyncTaskRecord task = repo.findById(taskId)
                .orElseThrow(() -> new SfcJsonException(SfcCommonError.RESOURCE_NOT_FOUND.getCode(), "任务id不存在"));

        // 如果任务是运行中的，则再获取最新的运行日志
        if (AsyncTaskConstants.Status.RUNNING.equals(task.getStatus())) {
            try {
                String runningLog = getRunningLog(task);
                if (SfcStringUtils.hasText(runningLog)) {
                    allLog += "[运行中最新日志]:\n" + runningLog;
                }
            } catch (Throwable e) {
                log.error("异步任务日志获取失败", e);
                allLog += "[运行中最新日志获取失败]:\n" + e.getMessage();
            }
        }
        return ResourceUtils.stringToResource(allLog);
    }

    /**
     * 获取运行中任务的最新日志。
     *
     * <p>执行者按批上报的运行日志会暂存在运行日志缓冲区中，本方法直接读取该缓冲区。</p>
     *
     * @param task 任务记录
     * @return 运行日志，获取不到时返回 null
     */
    private String getRunningLog(AsyncTaskRecord task) {
        List<String> buffered = logBufferService.read(task.getId());
        return buffered.isEmpty() ? null : String.join("\n", buffered);
    }

    @Override
    public long listenLog(Long taskId, Consumer<String> consumer) {
        String group = "log_group_" + SfcIdUtil.getId();
        return mqService.subscribeMessageQueue(TaskMQTopic.Get.ASYNC_TASK_LOG(taskId), group, msg -> consumer.accept(msg.body()));
    }

    @Override
    public void removeLogListen(Long listenId) {
        mqService.unsubscribeMessageQueue(listenId);
    }

    @Override
    public ProgressRecord getProgress(Long taskId) throws IOException {
        return reportService.getProgress(taskId);
    }

    @Override
    public void interrupt(Long taskId) throws IOException {
        AsyncTaskRecord record = repo.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("任务不存在"));

        // 若任务等待中，则直接修改数据库的任务状态，避免被执行者认领
        if (AsyncTaskConstants.Status.WAITING.equals(record.getStatus())) {
            boolean success = repo.updateStatus(taskId, AsyncTaskConstants.Status.CANCEL, AsyncTaskConstants.Status.WAITING) > 0;
            if (success) {
                saveCancelLog(record);
                return;
            }
        }

        if (!AsyncTaskConstants.Status.WAITING.equals(record.getStatus())
                && !AsyncTaskConstants.Status.RUNNING.equals(record.getStatus())) {
            throw new IllegalArgumentException("任务不在可中断的状态中");
        }

        boolean success = interruptRouter.interrupt(record);
        if (!success) {
            AsyncTaskRecord latest = repo.findById(taskId).orElse(null);
            if (latest != null && AsyncTaskConstants.Status.RUNNING.equals(latest.getStatus())) {
                throw new IllegalStateException("任务中断失败：执行者可能已失联或中断指令下发失败");
            }
        }
    }

    /**
     * 保存一个异步任务日志，记录被取消
     */
    private void saveCancelLog(AsyncTaskRecord record) {
        AsyncTaskLogRecord logRecord = new AsyncTaskLogRecord();
        logRecord.setLogInfo("[" + new Date() + "]任务被取消");
        logRecord.setTaskId(record.getId());
        logRecord.setUid(record.getUid());
        logRepo.save(logRecord);
    }

    @Override
    public AsyncTaskRecord waitTaskExit(Long taskId, long timeout, TimeUnit timeUnit) throws IOException, InterruptedException {
        // 获取任务当前状态，如果是已经完成的状态则直接返回
        AsyncTaskRecord asyncTaskRecord = repo.findById(taskId).orElseThrow(() -> new IllegalArgumentException("任务不存在"));
        Integer status = asyncTaskRecord.getStatus();
        if (AsyncTaskConstants.Status.FAILED.equals(status)) {
            asyncTaskRecord.setIsFailed(true);
            return asyncTaskRecord;
        }
        if (AsyncTaskConstants.Status.FINISH.equals(status)
                || AsyncTaskConstants.Status.CANCEL.equals(status)
                || AsyncTaskConstants.Status.OFFLINE.equals(status)) {
            return asyncTaskRecord;
        }

        // 信号量，默认占用1个，收到任务完成信号时释放
        Semaphore semaphore = new Semaphore(1);
        semaphore.acquire();

        // 订阅异步任务完成事件，收到该事件后则对锁进行解锁
        AtomicReference<AsyncTaskRecord> resRef = new AtomicReference<>();
        AtomicReference<Exception> exceptionRef = new AtomicReference<>();
        long subscribeId = mqService.subscribeBroadcast(ASYNC_TASK_EXIT(taskId), msg -> {
            try {
                resRef.set(msg.body());
            } catch (Exception e) {
                exceptionRef.set(e);
            } finally {
                semaphore.release();
            }
        });

        try {
            // 信号量要求阻塞操作，需要等待任务完成
            if (!semaphore.tryAcquire(timeout, timeUnit)) {
                throw new InterruptedException("wait task exit timeout");
            }
            Exception exception = exceptionRef.get();
            if (exception != null) {
                if (exception instanceof IOException) {
                    throw (IOException) exception;
                } else {
                    throw new RuntimeException(exception);
                }
            }
            return resRef.get();
        } finally {
            mqService.unsubscribe(subscribeId);
        }
    }

    @Override
    public AsyncTaskRecord rerun(Long taskId) throws IOException {
        AsyncTaskRecord originTask = repo.findById(taskId).orElseThrow(() -> new IllegalArgumentException("无效的任务id"));
        if (!SfcUIDValidator.validate(originTask.getUid(), true)) {
            throw new SfcJsonException(SfcCommonError.SYSTEM_FORBIDDEN);
        }
        if (AsyncTaskConstants.Status.RUNNING.equals(originTask.getStatus())
                || AsyncTaskConstants.Status.OFFLINE.equals(originTask.getStatus())
                || AsyncTaskConstants.Status.WAITING.equals(originTask.getStatus())) {
            throw new IllegalArgumentException("只能已结束的任务进行重新运行操作");
        }

        AsyncTaskRecord newTask = AsyncTaskRecord.builder()
                .taskType(originTask.getTaskType())
                .name(originTask.getName())
                .cpuOverhead(originTask.getCpuOverhead())
                .params(originTask.getParams())
                .build();
        newTask.setUid(Objects.requireNonNull(SecureUtils.getSpringSecurityUser()).getId());
        submitAsyncTask(newTask);
        return newTask;
    }
}
