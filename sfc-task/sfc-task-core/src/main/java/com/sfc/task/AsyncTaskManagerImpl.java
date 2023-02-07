package com.sfc.task;

import com.sfc.task.model.AsyncTaskLogRecord;
import com.sfc.task.model.AsyncTaskRecord;
import com.sfc.task.repo.AsyncTaskLogRecordRepo;
import com.sfc.task.repo.AsyncTaskRecordRepo;
import com.sfc.task.rpc.RPCManager;
import com.sfc.task.rpc.RPCRequest;
import com.sfc.task.rpc.RPCResponse;
import com.xiaotao.saltedfishcloud.common.prog.ProgressRecord;
import com.xiaotao.saltedfishcloud.utils.MapperHolder;
import com.xiaotao.saltedfishcloud.utils.ResourceUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
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
import java.util.Date;
import java.util.List;

/**
 * 默认的异步任务管理器实现
 * todo 实现咸鱼云进程退出后，任务自动失败并重发布机制（实现高可用）
 * todo 实现任务自动发布队列机制
 * todo 实现RPC日志获取
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
        executor.addTaskStartListener(record -> {
            // 乐观锁，切换任务状态为运行中，并用于检查该任务是否被取消
            boolean isCancel = repo.updateStatus(record.getId(), AsyncTaskConstants.Status.RUNNING, AsyncTaskConstants.Status.WAITING) == 0;
            if (isCancel) {
                // 标记为已取消
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
        AsyncTaskLogRecord logRecord = logRepo.findByTaskId(taskId);
        if (logRecord != null) {
            return ResourceUtils.stringToResource(logRecord.getLogInfo());
        } else {
            RPCRequest request = RPCRequest.builder()
                    .taskId(taskId)
                    .functionName(RPCFunction.TASK_GET_LOG)
                    .param(taskId + "")
                    .build();
            RPCResponse<String> response = rpcManager.call(request, String.class, Duration.ofMinutes(500));
            if (response != null && response.getIsSuccess()) {
                return ResourceUtils.stringToResource(response.getResult());
            }
        }
        return null;
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
}
