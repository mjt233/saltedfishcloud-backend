package com.sfc.task;

import com.sfc.rpc.annotation.RPCAction;
import com.sfc.rpc.annotation.RPCService;
import com.sfc.rpc.support.RPCContextHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RPCService(namespace = "async_task")
public class DefaultAsyncTaskRpcService {
    @Autowired
    private AsyncTaskExecutor executor;

    /**
     * 获取任务的执行日志
     * @param taskId        任务id
     */
    @RPCAction(ignoreMessage = "任务日志不存在")
    public String getTaskLog(Long taskId) throws IOException {
        return executor.getLog(taskId);
    }

    /**
     * 中断任务
     * @param taskId    任务id
     */
    @RPCAction("interrupt")
    public void interrupt(Long taskId) {
        AsyncTask task = executor.getTask(taskId);
        if (task == null) {
            RPCContextHolder.setIsIgnore(true);
        } else {
            task.interrupt();
        }
    }
}
