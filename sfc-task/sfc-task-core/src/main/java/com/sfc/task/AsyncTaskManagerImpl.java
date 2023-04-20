package com.sfc.task;

import com.sfc.task.model.AsyncTaskLogRecord;
import com.sfc.task.model.AsyncTaskRecord;
import com.sfc.task.repo.AsyncTaskLogRecordRepo;
import com.sfc.task.repo.AsyncTaskRecordRepo;
import com.sfc.rpc.RPCManager;
import com.sfc.rpc.RPCRequest;
import com.sfc.rpc.RPCResponse;
import com.sfc.task.prog.ProgressRecord;
import com.sfc.constant.error.CommonError;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.utils.MapperHolder;
import com.xiaotao.saltedfishcloud.utils.ResourceUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 默认的异步任务管理器实现
 */
@Component
@Slf4j
public class AsyncTaskManagerImpl implements AsyncTaskManager, InitializingBean {

    @Autowired
    private RPCManager rpcManager;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private AsyncTaskRecordRepo repo;

    @Autowired
    private AsyncTaskLogRecordRepo logRepo;

    @Autowired
    private AsyncTaskExecutor executor;

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
        RPCRequest request = RPCRequest.builder()
                .taskId(taskId)
                .functionName(RPCFunction.TASK_INTERRUPT)
                .build();
        rpcManager.call(request, Boolean.class, Duration.ofSeconds(30));
    }

    @Override
    public ProgressRecord getProgress(Long taskId) throws IOException {
        return executor.getProgress(taskId);
    }

    public void initRPCHandler() {
        // 注册获取任务日志方法
        rpcManager.registerRpcHandler(RPCFunction.TASK_GET_LOG, request -> {
            Resource resource = executor.getLog(Long.parseLong(request.getParam()), false);
            if (resource == null) {
                return RPCResponse.ingore();
            } else {
                try(InputStream inputStream = resource.getInputStream()) {
                    return RPCResponse.<String>builder()
                            .isHandled(true)
                            .isSuccess(true)
                            .result(StreamUtils.copyToString(inputStream, StandardCharsets.UTF_8))
                            .build();
                } catch (IOException e) {
                    log.error("获取异步任务日志出错: {}", request, e);
                    return RPCResponse.<String>builder()
                            .isHandled(true)
                            .isSuccess(false)
                            .error(e.getMessage())
                            .build();
                }
            }
        });

        // 注册中断任务方法
        rpcManager.registerRpcHandler(RPCFunction.TASK_INTERRUPT, request -> {
            Long taskId = request.getTaskId();
            AsyncTask task = executor.getTask(taskId);
            if (task == null) {
                return RPCResponse.ingore();
            } else {
                try {
                    task.interrupt();
                } catch (Exception e) {
                    log.error("任务中断出错: ", e);
                    return RPCResponse.error(e.getMessage());
                }
            }
            return RPCResponse.success(true);
        });
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
            record.setStatus(AsyncTaskConstants.Status.FAILED);
            record.setFailedDate(new Date());
            repo.save(record);
        });
        executor.addTaskFinishListener(record -> {
            record.setStatus(AsyncTaskConstants.Status.FINISH);
            record.setFinishDate(new Date());
            repo.save(record);
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
            RPCRequest request = RPCRequest.builder()
                    .taskId(taskId)
                    .functionName(RPCFunction.TASK_GET_LOG)
                    .param(taskId + "")
                    .build();
            RPCResponse<String> response = rpcManager.call(request, String.class, Duration.ofMinutes(500));
            if (response != null) {
                if(response.getIsSuccess()) {
                    allLog += "[运行中最新日志]:\n" + response.getResult();
                } else {
                    allLog += "[运行中最新日志获取失败]:\n" + response.getError();
                }
            } else {
                allLog += "[运行中最新日志无响应]\n";
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
        initRPCHandler();
    }

    @EventListener(ContextClosedEvent.class)
    public void stop() {
        Collection<Long> stopIds = executor.stop();
        if (!stopIds.isEmpty()) {
            repo.setTaskOffline(stopIds);
        }
    }
}
