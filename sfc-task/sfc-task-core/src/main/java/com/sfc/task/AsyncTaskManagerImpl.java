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
    public ProgressRecord getProgress(Long taskId) throws IOException {
        return executor.getProgress(taskId);
    }

    public void initRPCHandler() {
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
    }

    public void initExecutor() {
        executor.addTaskStartListener(record -> {
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
