package com.sfc.task;

import com.sfc.cloud.data.id.SfcIdUtil;
import com.sfc.cloud.data.validation.SfcUIDValidator;
import com.sfc.common.constant.SfcCommonError;
import com.sfc.common.constant.SfcUserConstants;
import com.sfc.common.exception.SfcJsonException;
import com.sfc.common.utils.SfcStringUtils;
import com.sfc.rpc.annotation.RPCResource;
import com.sfc.task.constants.TaskMQTopic;
import com.sfc.task.model.AsyncTaskCreateParam;
import com.sfc.task.model.AsyncTaskLogRecord;
import com.sfc.task.model.AsyncTaskRecord;
import com.sfc.task.prog.ProgressDetector;
import com.sfc.task.prog.ProgressRecord;
import com.sfc.task.report.TaskLogBufferService;
import com.sfc.task.repo.AsyncTaskLogRecordRepo;
import com.sfc.task.repo.AsyncTaskRecordRepo;
import com.sfc.task.schedule.TaskScheduleConstants;
import com.sfc.task.worker.LocalTaskWorker;
import com.sfc.task.worker.TaskInterruptRouter;
import com.sfc.task.worker.TaskWorkerInfo;
import com.sfc.task.worker.TaskWorkerKind;
import com.sfc.task.worker.TaskWorkerRegistryService;
import com.xiaotao.saltedfishcloud.service.mq.MQService;
import com.xiaotao.saltedfishcloud.utils.ResourceUtils;
import com.xiaotao.saltedfishcloud.utils.SecureUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
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
 *     <li>统一查询：日志、进度、状态，不区分任务由 core 还是微服务执行；</li>
 *     <li>执行控制：中断请求按任务的认领者路由到对应执行者；</li>
 *     <li>优雅停机：把本节点正在执行的任务重置回队列，交由其他节点或重启后的本节点继续执行。</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultAsyncTaskManagerImpl implements AsyncTaskManager, InitializingBean {
    private static final String LOG_PREFIX = "[异步任务管理]";

    private final AsyncTaskRecordRepo repo;

    private final AsyncTaskLogRecordRepo logRepo;

    private final AsyncTaskExecutor executor;

    private final MQService mqService;

    private final TaskWorkerRegistryService workerRegistryService;

    private final TaskInterruptRouter interruptRouter;

    private final TaskLogBufferService logBufferService;

    private final ProgressDetector progressDetector;

    private final LocalTaskWorker localTaskWorker;

    @RPCResource
    private DefaultAsyncTaskRpcService asyncTaskRpcService;

    /**
     * 将spring容器中所有实现了AsyncTaskFactory的bean都注册到任务管理器中
     */
    @Autowired(required = false)
    public void setFactories(List<AsyncTaskFactory> factories) {
        if (factories != null && !factories.isEmpty()) {
            for (AsyncTaskFactory factory : factories) {
                registerFactory(factory);
            }
        }
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        initExecutor();
    }

    /**
     * 注册任务执行过程中的状态流转监听器。
     */
    public void initExecutor() {
        // 任务开始时，记录执行节点信息（队列任务在认领时已写入，直接执行的任务在这里补充）
        executor.addTaskStartListener(record -> {
            if (AsyncTaskConstants.Status.CANCEL.equals(record.getStatus())) {
                return;
            }
            if (!SfcStringUtils.hasText(record.getClaimedWorker())) {
                record.setClaimedWorker(workerRegistryService.getLocalWorkerId());
            }
            record.setExecuteDate(new Date());
            record.setStatus(AsyncTaskConstants.Status.RUNNING);
            record.setExecutor(record.getClaimedWorker());
            record.setLastReportAt(new Date());
            repo.save(record);
        });

        // 记录任务失败信息
        executor.addTaskFailedListener(record -> {
            try {
                record.setStatus(Optional.ofNullable(record.getStatus()).orElse(AsyncTaskConstants.Status.CANCEL));
                record.setFailedDate(new Date());
                record.setIsFailed(true);
                repo.save(record);
            } finally {
                mqService.sendBroadcast(ASYNC_TASK_EXIT(record.getId()), record);
            }
        });

        // 记录任务成功信息
        executor.addTaskFinishListener(record -> {
            try {
                record.setStatus(AsyncTaskConstants.Status.FINISH);
                record.setFinishDate(new Date());
                record.setFailedDate(null);
                repo.save(record);
            } finally {
                mqService.sendBroadcast(ASYNC_TASK_EXIT(record.getId()), record);
            }
        });
    }

    @Override
    public void registerFactory(AsyncTaskFactory factory) {
        executor.registerFactory(factory);
    }

    @Override
    public void submitAsyncTask(AsyncTaskRecord record) throws IOException {
        record.setStatus(AsyncTaskConstants.Status.WAITING);
        record.setClaimedWorker(null);
        record.setClaimedAt(null);
        repo.save(record);
        mqService.sendBroadcast(TaskMQTopic.TASK_PUBLISH, record.getId());
        log.info("{}提交任务 {}-{}，类型: {}", LOG_PREFIX, record.getName(), record.getId(), record.getTaskType());
    }

    @Override
    public void executeAsyncTask(AsyncTaskRecord record) throws IOException {
        repo.save(record);
        executor.execute(record);
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
                .build();
        record.setUid(uid == null ? SfcUserConstants.PUBLIC_USER_ID : uid);

        if (Boolean.TRUE.equals(param.getImmediate())) {
            executeAsyncTask(record);
        } else {
            submitAsyncTask(record);
        }
        return record;
    }

    @Override
    public Resource getTaskLog(Long taskId, boolean withHistory) throws IOException {
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
     * 获取运行中任务的最新日志。根据任务认领者类型选择日志来源：
     * 本地任务读取本地日志文件，微服务任务读取日志缓冲，其他 core 节点任务通过集群 RPC 读取。
     */
    private String getRunningLog(AsyncTaskRecord task) throws IOException {
        String workerId = task.getClaimedWorker();
        if (!SfcStringUtils.hasText(workerId)) {
            return null;
        }
        if (workerId.equals(workerRegistryService.getLocalWorkerId())) {
            return executor.getLog(task.getId());
        }
        List<String> buffered = logBufferService.read(task.getId());
        if (!buffered.isEmpty()) {
            return String.join("\n", buffered);
        }
        TaskWorkerInfo worker = workerRegistryService.get(workerId);
        if (worker != null && worker.getKind() == TaskWorkerKind.LOCAL_NODE) {
            try {
                return asyncTaskRpcService.getTaskLog(task.getId());
            } catch (Throwable e) {
                log.debug("{}通过集群RPC获取任务 {} 运行日志失败: {}", LOG_PREFIX, task.getId(), e.getMessage());
            }
        }
        return null;
    }

    @Override
    public AsyncTaskExecutor getExecutor() {
        return executor;
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
        return progressDetector.getRecord(taskId.toString());
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

    /**
     * 容器关闭时优雅停机：
     * <ol>
     *     <li>先停止本地 worker 的认领循环，避免刚回收的任务被本节点重新认领；</li>
     *     <li>中断并放弃本节点正在执行的任务；</li>
     *     <li>把放弃的任务重置回等待中状态，交由其他节点或重启后的本节点继续执行。</li>
     * </ol>
     */
    @EventListener(ContextClosedEvent.class)
    public void stop() {
        try {
            localTaskWorker.stop();
        } catch (Exception e) {
            log.error("{}停止本地任务worker失败", LOG_PREFIX, e);
        }
        Collection<Long> stopIds = executor.stop();
        List<Long> requeued = new ArrayList<>();
        for (Long taskId : stopIds) {
            try {
                if (repo.abandonAndRequeue(taskId, TaskScheduleConstants.MAX_RETRY) > 0) {
                    requeued.add(taskId);
                } else {
                    repo.failIfRetryExceeded(taskId, TaskScheduleConstants.MAX_RETRY);
                }
            } catch (Exception e) {
                log.error("{}停机时回收任务 {} 失败", LOG_PREFIX, taskId, e);
            }
        }
        log.info("{}已停止，已放弃并重新排队的任务: {}", LOG_PREFIX, requeued);
    }
}
