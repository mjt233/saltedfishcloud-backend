package com.sfc.task;

import com.sfc.task.model.AsyncTaskRecord;
import com.sfc.task.repo.AsyncTaskRecordRepo;
import com.sfc.task.rpc.RPCManager;
import com.xiaotao.saltedfishcloud.utils.MapperHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Date;
import java.util.List;

/**
 * 默认的异步任务管理器实现
 * todo 实现咸鱼云进程退出后，任务自动失败并重发布机制（实现高可用）
 * todo 实现任务自动发布队列机制
 * todo 实现RPC日志获取
 */
public class AsyncTaskManagerImpl implements AsyncTaskManager {

    @Autowired
    private RPCManager rpcManager;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private AsyncTaskRecordRepo repo;

    private AsyncTaskExecutor executor;

    public AsyncTaskManagerImpl(AsyncTaskExecutor executor) {
        this.executor = executor;
        initExecutor();
    }


    @Autowired(required = false)
    public void setFactories(List<AsyncTaskFactory> factories) {
        if (factories != null && !factories.isEmpty()) {
            for (AsyncTaskFactory factory : factories) {
                registerFactory(factory);
            }
        }
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
    public InputStream getTaskLog(Long taskId, boolean withHistory) {

        return null;
    }

    @Override
    public AsyncTaskExecutor getExecutor() {
        return executor;
    }
}
