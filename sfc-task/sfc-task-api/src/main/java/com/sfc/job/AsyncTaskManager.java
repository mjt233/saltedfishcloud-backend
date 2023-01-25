package com.sfc.job;

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
}
