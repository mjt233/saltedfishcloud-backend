package com.sfc.job;

import java.io.InputStream;

/**
 * 异步任务管理器
 */
public interface AsyncTaskManager {
    /**
     * 注册一个任务工厂
     * @param type 任务类型
     * @param factory 该类型对应的任务工厂
     */
    void registerFactory(String type, AsyncTaskFactory factory);

    /**
     * 提交异步任务到系统执行
     * @param type      任务类型
     * @param record    任务记录
     */
    void submitAsyncTask(String type, AsyncTaskRecord record);

    /**
     * 获取任务的执行日志流
     * @param taskId        任务id
     * @param withHistory   是否需要包含该任务的历史日志数据。若不包含则表示只获取实时产生的日志数据。（目前暂不支持实时流）
     * @return              日志数据的输入流
     */
    InputStream getTaskLog(Long taskId, boolean withHistory);
}
