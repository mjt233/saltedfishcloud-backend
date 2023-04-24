package com.sfc.task;

import com.sfc.task.model.AsyncTaskRecord;
import com.sfc.task.prog.ProgressRecord;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.util.function.Consumer;

/**
 * 异步任务管理器
 */
public interface AsyncTaskManager {

    /**
     * 中断一个任务执行
     * @param taskId    任务id
     */
    void interrupt(Long taskId) throws IOException;

    /**
     * 阻塞等待任务完成
     * @param taskId        任务id
     * @param timeout       最长超时等待时间（ms），负数表示不限制
     * @return              任务信息，包含是否失败字段
     */
    AsyncTaskRecord waitTaskExit(Long taskId, long timeout) throws IOException;

    /**
     * 监听任务完成，当任务完成时触发回调
     * @param taskId        任务id
     * @param timeout       最长超时等待时间（ms），负数表示不限制
     * @param consumer      任务完成时触发的消费函数，参数为任务信息，包含是否失败字段
     */
    void onTaskExit(Long taskId, long timeout, Consumer<AsyncTaskRecord> consumer) throws IOException;

    /**
     * 注册一个任务工厂
     * @param factory 该类型对应的任务工厂
     */
    void registerFactory(AsyncTaskFactory factory);

    /**
     * 提交异步任务到系统执行
     * @param record    任务记录
     */
    void submitAsyncTask(AsyncTaskRecord record) throws IOException;

    /**
     * 获取任务的执行日志流
     * @param taskId        任务id
     * @param withHistory   是否需要包含该任务的历史日志数据。若不包含则表示只获取实时产生的日志数据。（目前暂不支持实时流）
     * @return              日志数据的输入流
     */
    Resource getTaskLog(Long taskId, boolean withHistory) throws IOException;

    /**
     * 获取任务进度
     * @param taskId 任务id
     */
    ProgressRecord getProgress(Long taskId) throws IOException;

    /**
     * 获取异步任务执行器
     */
    AsyncTaskExecutor getExecutor();
}
