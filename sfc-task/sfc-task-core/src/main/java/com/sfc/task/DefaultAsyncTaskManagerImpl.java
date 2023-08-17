package com.sfc.task;

import com.sfc.constant.MQTopic;
import com.sfc.constant.error.CommonError;
import com.sfc.rpc.annotation.RPCResource;
import com.sfc.task.model.AsyncTaskLogRecord;
import com.sfc.task.model.AsyncTaskRecord;
import com.sfc.task.prog.ProgressRecord;
import com.sfc.task.repo.AsyncTaskLogRecordRepo;
import com.sfc.task.repo.AsyncTaskRecordRepo;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.service.MQService;
import com.xiaotao.saltedfishcloud.utils.MapperHolder;
import com.xiaotao.saltedfishcloud.utils.ResourceUtils;
import com.xiaotao.saltedfishcloud.utils.SecureUtils;
import com.xiaotao.saltedfishcloud.utils.identifier.IdUtil;
import com.xiaotao.saltedfishcloud.validator.UIDValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * 默认的异步任务管理器实现
 */
@Component
@Slf4j
public class DefaultAsyncTaskManagerImpl implements AsyncTaskManager, InitializingBean {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private AsyncTaskRecordRepo repo;

    @Autowired
    private AsyncTaskLogRecordRepo logRepo;

    @Autowired
    private AsyncTaskExecutor executor;

    @Autowired
    private MQService mqService;

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
    public AsyncTaskRecord waitTaskExit(Long taskId, long timeout, TimeUnit timeUnit) throws IOException, InterruptedException {
        // 获取任务当前状态，如果是已经完成的状态则直接返回
        AsyncTaskRecord asyncTaskRecord = repo.findById(taskId).orElseThrow(() -> new IllegalArgumentException("任务不存在"));
        Integer status = asyncTaskRecord.getStatus();
        if (AsyncTaskConstants.Status.FAILED.equals(status)) {
            asyncTaskRecord.setIsFailed(true);
            return asyncTaskRecord;
        } else if (AsyncTaskConstants.Status.CANCEL.equals(status) || AsyncTaskConstants.Status.OFFLINE.equals(status)) {
            return asyncTaskRecord;
        }

        // 信号量，默认占用1个，收到任务完成信号时释放
        Semaphore semaphore = new Semaphore(1);
        semaphore.acquire();


        // 订阅异步任务完成事件，收到该事件后则对锁进行解锁
        AtomicReference<AsyncTaskRecord> resRef = new AtomicReference<>();
        AtomicReference<IOException> exceptionRef = new AtomicReference<>();
        long subscribeId = mqService.subscribeBroadcast(MQTopic.Prefix.ASYNC_TASK_EXIT + taskId, msg -> {
            try {
                resRef.set(MapperHolder.parseAsJson(msg.getBody(), AsyncTaskRecord.class));
            } catch (IOException e) {
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
            IOException exception = exceptionRef.get();
            if (exception != null) {
                throw exception;
            }
            return resRef.get();
        } finally {
            mqService.unsubscribe(subscribeId);
        }
    }

    @Override
    public void interrupt(Long taskId) throws IOException {
        AsyncTaskRecord record = repo.getOne(taskId);

        // 若任务等待中，则直接修改数据库的任务状态
        if (AsyncTaskConstants.Status.WAITING.equals(record.getStatus())) {
            // 乐观锁机制下若修改失败，则任务可能在修改前被调度执行，状态被更新为执行中了。此时需要发起RPC通知执行的节点中断任务
            boolean success = repo.updateStatus(taskId, AsyncTaskConstants.Status.CANCEL, AsyncTaskConstants.Status.WAITING) > 0;
            if (success) {
                saveCancelLog(record);
                return;
            }
        }

        if (!AsyncTaskConstants.Status.WAITING.equals(record.getStatus()) && !AsyncTaskConstants.Status.RUNNING.equals(record.getStatus())) {
            throw new IllegalArgumentException("任务不在可中断的状态中");
        }

        // 发起RPC让正在执行该任务的节点中断执行
        asyncTaskRpcService.interrupt(taskId);
    }

    @Override
    public ProgressRecord getProgress(Long taskId) throws IOException {
        return executor.getProgress(taskId);
    }

    /**
     * 保存一个异步任务日志，记录被取消
     */
    private void saveCancelLog(AsyncTaskRecord record) {
        AsyncTaskLogRecord logRecord = new AsyncTaskLogRecord();
        logRecord.setLogInfo("[" + new Date() +  "]任务被取消");
        logRecord.setTaskId(record.getId());
        logRecord.setUid(record.getUid());
        logRepo.save(logRecord);
    }

    public void initExecutor() {
        // 任务开始时，记录执行节点信息，并确保获得执行权（确保任务排队、任务取消、任务执行三者操作互斥）
        executor.addTaskStartListener(record -> {
            if (AsyncTaskConstants.Status.CANCEL.equals(record.getStatus())) {
                return;
            }

            // 乐观锁，切换任务状态为运行中，并用于检查该任务是否被取消
            boolean isCancel = repo.updateStatus(record.getId(), AsyncTaskConstants.Status.RUNNING, AsyncTaskConstants.Status.WAITING) == 0;
            if (isCancel) {
                // 标记为已取消，执行器在执行完事件后会判断这个状态，如果为已取消则不会执行任务
                record.setStatus(AsyncTaskConstants.Status.CANCEL);

                // 日志标记为已取消
                saveCancelLog(record);
                return;
            }

            String hostName;
            try {
                hostName = InetAddress.getLocalHost().getHostName();
            } catch (UnknownHostException e) {
                e.printStackTrace();
                hostName = "unknown";
            }
            record.setExecuteDate(new Date());
            record.setStatus(AsyncTaskConstants.Status.RUNNING);
            record.setExecutor(hostName);
            repo.save(record);
        });

        // 记录任务失败/成功信息
        executor.addTaskFailedListener(record -> {
            try {
                record.setStatus(Optional.ofNullable(record.getStatus()).orElse(AsyncTaskConstants.Status.CANCEL));
                record.setFailedDate(new Date());
                record.setIsFailed(true);
                repo.save(record);
            } finally {
                mqService.sendBroadcast(MQTopic.Prefix.ASYNC_TASK_EXIT + record.getId(), record);
            }
        });
        executor.addTaskFinishListener(record -> {
            try {
                record.setStatus(AsyncTaskConstants.Status.FINISH);
                record.setFinishDate(new Date());
                record.setFailedDate(null);
                repo.save(record);
            } finally {
                mqService.sendBroadcast(MQTopic.Prefix.ASYNC_TASK_EXIT + record.getId(), record);
            }
        });

        // 收到不支持的任务类型时，重新发布
        executor.addUnsupportedListener(record -> {
            try {
                Thread.sleep(1000);
                log.warn("不支持的任务类型:{}，重新发布", record.getTaskType());
                this.submitAsyncTask(record);
            } catch (Exception e) {
                log.error("不受支持的任务重释放失败", e);
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
        repo.save(record);
        redisTemplate.opsForList().leftPush(AsyncTaskConstants.RedisKey.TASK_QUEUE, MapperHolder.toJson(record));
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
        AsyncTaskRecord task = repo.findById(taskId).orElseThrow(() -> new JsonException(CommonError.RESOURCE_NOT_FOUND.getCode(), "任务id不存在"));

        // 如果任务是运行中的，则再获取最新的日志
        if (AsyncTaskConstants.Status.RUNNING.equals(task.getStatus())) {
            try {
                allLog += "[运行中最新日志]:\n" +asyncTaskRpcService.getTaskLog(taskId);
            } catch (Throwable e) {
                log.error("异步任务日志获取失败", e);
                allLog += "[运行中最新日志获取失败]:\n" + e.getMessage();
            }
        }
        return ResourceUtils.stringToResource(allLog);
    }

    @Override
    public AsyncTaskExecutor getExecutor() {
        return executor;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        initExecutor();
    }

    @EventListener(ContextClosedEvent.class)
    public void stop() {
        Collection<Long> stopIds = executor.stop();
        if (!stopIds.isEmpty()) {
            repo.setTaskOffline(stopIds);
        }
    }

    @Override
    public long listenLog(Long taskId, Consumer<String> consumer) {
        String group = "log_group_" + IdUtil.getId();
        return mqService.subscribeMessageQueue(MQTopic.Prefix.ASYNC_TASK_LOG + taskId, group, msg -> {
            try {
                consumer.accept(MapperHolder.parseAsJson(msg.getBody(), String.class));
            } catch (IOException e) {
                log.error("[异步任务管理器]监听任务{}的日志出错", taskId, e);
            }
        });
    }

    @Override
    public void removeLogListen(Long listenId) {
        mqService.unsubscribeMessageQueue(listenId);
    }

    @Override
    public AsyncTaskRecord rerun(Long taskId) throws IOException {
        AsyncTaskRecord originTask = repo.findById(taskId).orElseThrow(() -> new IllegalArgumentException("无效的任务id"));
        if(!UIDValidator.validate(originTask.getUid(), true)) {
            throw new JsonException(CommonError.SYSTEM_FORBIDDEN);
        }
        if (
                AsyncTaskConstants.Status.RUNNING.equals(originTask.getStatus()) ||
                AsyncTaskConstants.Status.OFFLINE.equals(originTask.getStatus()) ||
                AsyncTaskConstants.Status.WAITING.equals(originTask.getStatus())
        ) {
            throw new IllegalArgumentException("只能已结束的任务进行重新运行操作");
        }

        AsyncTaskRecord newTask = AsyncTaskRecord.builder()
                .taskType(originTask.getTaskType())
                .name(originTask.getName())
                .cpuOverhead(originTask.getCpuOverhead())
                .params(originTask.getParams())
                .build();
        newTask.setUid((long) Objects.requireNonNull(SecureUtils.getSpringSecurityUser()).getId());
        submitAsyncTask(newTask);
        return newTask;
    }
}
